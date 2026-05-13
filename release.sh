#!/usr/bin/env bash
# =============================================================================
# Loudr Release Script
# Usage:
#   ./release.sh              → interactive mode (asks for bump type)
#   ./release.sh patch        → bump patch:  1.0.8 → 1.0.9
#   ./release.sh minor        → bump minor:  1.0.8 → 1.1.0
#   ./release.sh major        → bump major:  1.0.8 → 2.0.0
#   ./release.sh 1.2.3        → set exact version
#
# Flags:
#   --no-build                → skip the bundleRelease step
#   --no-tag                  → skip creating a git tag
#   --no-push                 → skip git push
# =============================================================================
set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[0;33m'
BLU='\033[0;34m'; CYN='\033[0;36m'; BLD='\033[1m'; RST='\033[0m'

info()    { echo -e "${BLU}ℹ ${RST}$*"; }
success() { echo -e "${GRN}✔ ${RST}$*"; }
warn()    { echo -e "${YLW}⚠ ${RST}$*"; }
error()   { echo -e "${RED}✘ ${RST}$*" >&2; exit 1; }
header()  { echo -e "\n${BLD}${CYN}$*${RST}"; }

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
BUNDLE_OUT="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"

[[ -f "$GRADLE" ]] || error "Cannot find app/build.gradle.kts — run this from the project root."

# ── Parse flags ───────────────────────────────────────────────────────────────
DO_BUILD=true; DO_TAG=true; DO_PUSH=true; BUMP_ARG=""

for arg in "$@"; do
  case "$arg" in
    --no-build) DO_BUILD=false ;;
    --no-tag)   DO_TAG=false ;;
    --no-push)  DO_PUSH=false ;;
    -*)         error "Unknown flag: $arg" ;;
    *)          BUMP_ARG="$arg" ;;
  esac
done

# ── Read current version ──────────────────────────────────────────────────────
CUR_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE")
CUR_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE")

[[ -z "$CUR_CODE" || -z "$CUR_NAME" ]] && error "Could not parse version from build.gradle.kts"

IFS='.' read -r V_MAJOR V_MINOR V_PATCH <<< "$CUR_NAME"

header "═══════════════════════════════════════"
header "  Loudr Release Helper"
header "═══════════════════════════════════════"
info "Current version : ${BLD}${CUR_NAME}${RST}  (versionCode ${CUR_CODE})"

# ── Determine new version ─────────────────────────────────────────────────────
if [[ -z "$BUMP_ARG" ]]; then
  echo ""
  echo -e "  ${BLD}How do you want to bump the version?${RST}"
  echo -e "  [1] patch  ${CUR_NAME} → ${V_MAJOR}.${V_MINOR}.$((V_PATCH + 1))"
  echo -e "  [2] minor  ${CUR_NAME} → ${V_MAJOR}.$((V_MINOR + 1)).0"
  echo -e "  [3] major  ${CUR_NAME} → $((V_MAJOR + 1)).0.0"
  echo -e "  [4] custom (enter manually)"
  echo ""
  read -rp "  Choice [1]: " choice
  choice="${choice:-1}"
  case "$choice" in
    1|patch) BUMP_ARG="patch" ;;
    2|minor) BUMP_ARG="minor" ;;
    3|major) BUMP_ARG="major" ;;
    4|custom)
      read -rp "  Enter new version (e.g. 2.0.0): " BUMP_ARG
      ;;
    *) error "Invalid choice." ;;
  esac
fi

case "$BUMP_ARG" in
  patch) NEW_NAME="${V_MAJOR}.${V_MINOR}.$((V_PATCH + 1))" ;;
  minor) NEW_NAME="${V_MAJOR}.$((V_MINOR + 1)).0" ;;
  major) NEW_NAME="$((V_MAJOR + 1)).0.0" ;;
  [0-9]*.[0-9]*.[0-9]*)
    NEW_NAME="$BUMP_ARG"
    ;;
  *) error "Invalid version argument: '$BUMP_ARG'. Use patch/minor/major or X.Y.Z" ;;
esac

NEW_CODE=$((CUR_CODE + 1))

# ── Confirm ───────────────────────────────────────────────────────────────────
echo ""
echo -e "  ${BLD}Release plan:${RST}"
echo -e "  versionName   ${CUR_NAME}  →  ${GRN}${NEW_NAME}${RST}"
echo -e "  versionCode   ${CUR_CODE}  →  ${GRN}${NEW_CODE}${RST}"
echo -e "  Build bundle  : $(${DO_BUILD} && echo yes || echo no)"
echo -e "  Git tag       : $(${DO_TAG}   && echo "v${NEW_NAME}" || echo no)"
echo -e "  Git push      : $(${DO_PUSH}  && echo yes || echo no)"
echo ""
read -rp "  Proceed? [Y/n]: " confirm
confirm="${confirm:-Y}"
[[ "$confirm" =~ ^[Yy]$ ]] || { warn "Aborted."; exit 0; }

# ── Apply version bump ────────────────────────────────────────────────────────
header "Updating build.gradle.kts…"

# Use a temp file for safe in-place editing
TMP=$(mktemp)
sed \
  -e "s/versionCode\s*=\s*${CUR_CODE}/versionCode     = ${NEW_CODE}/" \
  -e "s/versionName\s*=\s*\"${CUR_NAME}\"/versionName     = \"${NEW_NAME}\"/" \
  "$GRADLE" > "$TMP"
mv "$TMP" "$GRADLE"

# Verify
VERIFY_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE")
VERIFY_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE")
[[ "$VERIFY_CODE" == "$NEW_CODE" && "$VERIFY_NAME" == "$NEW_NAME" ]] \
  && success "build.gradle.kts updated to ${NEW_NAME} (${NEW_CODE})" \
  || error "Version update failed — please check build.gradle.kts manually"

# ── Build release bundle ──────────────────────────────────────────────────────
if $DO_BUILD; then
  header "Building release bundle…"
  cd "$SCRIPT_DIR"
  if ./gradlew bundleRelease; then
    success "Bundle built: $BUNDLE_OUT"
    # Print file size
    if [[ -f "$BUNDLE_OUT" ]]; then
      SIZE=$(du -h "$BUNDLE_OUT" | cut -f1)
      info "Bundle size: ${SIZE}"
    fi
  else
    error "bundleRelease failed — version bump has been applied but build was not successful."
  fi
fi

# ── Git operations ────────────────────────────────────────────────────────────
header "Git operations…"

# Check if git repo
if ! git -C "$SCRIPT_DIR" rev-parse --git-dir &>/dev/null; then
  warn "Not a git repository — skipping tag and push."
  DO_TAG=false; DO_PUSH=false
fi

if $DO_TAG || $DO_PUSH; then
  TAG="v${NEW_NAME}"

  # Stage the version bump
  git -C "$SCRIPT_DIR" add app/build.gradle.kts
  git -C "$SCRIPT_DIR" commit -m "chore: bump version to ${NEW_NAME} (${NEW_CODE})" \
    && success "Committed version bump"

  if $DO_TAG; then
    git -C "$SCRIPT_DIR" tag -a "$TAG" -m "Release ${TAG}"
    success "Created tag ${TAG}"
  fi

  if $DO_PUSH; then
    git -C "$SCRIPT_DIR" push
    $DO_TAG && git -C "$SCRIPT_DIR" push origin "$TAG"
    success "Pushed to remote"
  fi
fi

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BLD}${GRN}═══════════════════════════════════════${RST}"
echo -e "${BLD}${GRN}  Loudr ${NEW_NAME} ready to ship! 🚀${RST}"
echo -e "${BLD}${GRN}═══════════════════════════════════════${RST}"
if $DO_BUILD && [[ -f "$BUNDLE_OUT" ]]; then
  echo -e "  AAB: ${BLD}${BUNDLE_OUT}${RST}"
fi
echo ""
