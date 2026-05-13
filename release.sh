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
#   --no-upload               → skip upload to Play Console
#   --track=<track>           → Play track: internal (default), beta, production
#   --notes="<text>"          → "What's new" release notes (max 500 chars)
# =============================================================================
set -euo pipefail

# Ensure local gem bin is in PATH for fastlane
if command -v ruby &> /dev/null; then
  GEM_BIN="$(ruby -e 'puts Gem.user_dir')/bin"
  if [[ -d "$GEM_BIN" ]]; then
    export PATH="$GEM_BIN:$PATH"
  fi
fi

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
DO_BUILD=true; DO_TAG=true; DO_PUSH=true; DO_UPLOAD=true
UPLOAD_TRACK="internal"; RELEASE_NOTES=""; BUMP_ARG=""

for arg in "$@"; do
  case "$arg" in
    --no-build)    DO_BUILD=false ;;
    --no-tag)      DO_TAG=false ;;
    --no-push)     DO_PUSH=false ;;
    --no-upload)   DO_UPLOAD=false ;;
    --track=*)     UPLOAD_TRACK="${arg#--track=}" ;;
    --notes=*)     RELEASE_NOTES="${arg#--notes=}" ;;
    -*)            error "Unknown flag: $arg" ;;
    *)             BUMP_ARG="$arg" ;;
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
  none)  NEW_NAME="$CUR_NAME"
         NEW_CODE="$CUR_CODE" ;;
  patch) NEW_NAME="${V_MAJOR}.${V_MINOR}.$((V_PATCH + 1))"
         NEW_CODE=$((CUR_CODE + 1)) ;;
  minor) NEW_NAME="${V_MAJOR}.$((V_MINOR + 1)).0"
         NEW_CODE=$((CUR_CODE + 1)) ;;
  major) NEW_NAME="$((V_MAJOR + 1)).0.0"
         NEW_CODE=$((CUR_CODE + 1)) ;;
  [0-9]*.[0-9]*.[0-9]*)
    NEW_NAME="$BUMP_ARG"
    NEW_CODE=$((CUR_CODE + 1))
    ;;
  *) error "Invalid version argument: '$BUMP_ARG'. Use patch/minor/major, X.Y.Z, or none" ;;
esac

# ── Confirm ───────────────────────────────────────────────────────────────────
echo ""
echo -e "  ${BLD}Release plan:${RST}"
echo -e "  versionName   ${CUR_NAME}  →  ${GRN}${NEW_NAME}${RST}"
echo -e "  versionCode   ${CUR_CODE}  →  ${GRN}${NEW_CODE}${RST}"
echo -e "  Build bundle  : $(${DO_BUILD}   && echo yes || echo no)"
echo -e "  Git tag       : $(${DO_TAG}     && echo "v${NEW_NAME}" || echo no)"
echo -e "  Git push      : $(${DO_PUSH}    && echo yes || echo no)"
echo -e "  Play upload   : $(${DO_UPLOAD}  && echo "${UPLOAD_TRACK}" || echo no)"
if $DO_UPLOAD && [[ -n "$RELEASE_NOTES" ]]; then
  echo -e "  Release notes : ${RELEASE_NOTES:0:60}$([ ${#RELEASE_NOTES} -gt 60 ] && echo '…')"
fi
echo ""
read -rp "  Proceed? [Y/n]: " confirm
confirm="${confirm:-Y}"
[[ "$confirm" =~ ^[Yy]$ ]] || { warn "Aborted."; exit 0; }

# ── Apply version bump ────────────────────────────────────────────────────────
if [[ "$BUMP_ARG" != "none" ]]; then
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
fi

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

  # Stage the version bump if there are changes
  if git -C "$SCRIPT_DIR" diff --cached --quiet app/build.gradle.kts && git -C "$SCRIPT_DIR" diff --quiet app/build.gradle.kts; then
    info "No version changes to commit."
  else
    git -C "$SCRIPT_DIR" add app/build.gradle.kts
    git -C "$SCRIPT_DIR" commit -m "chore: bump version to ${NEW_NAME} (${NEW_CODE})" || error "Failed to commit version bump"
    success "Committed version bump"
  fi

  if $DO_TAG; then
    if git -C "$SCRIPT_DIR" rev-parse "$TAG" >/dev/null 2>&1; then
      warn "Tag $TAG already exists — skipping tag creation."
    else
      git -C "$SCRIPT_DIR" tag -a "$TAG" -m "Release ${TAG}" || error "Failed to create tag $TAG"
      success "Created tag ${TAG}"
    fi
  fi

  if $DO_PUSH; then
    git -C "$SCRIPT_DIR" push || error "Failed to push to remote"
    if $DO_TAG; then
      git -C "$SCRIPT_DIR" push origin "$TAG" || error "Failed to push tag $TAG"
    fi
    success "Pushed to remote"
  fi
fi

# ── Upload to Play Console ────────────────────────────────────────────────────
if $DO_UPLOAD; then
  if [[ ! -f "$BUNDLE_OUT" ]]; then
    error "--upload requires a built AAB — cannot proceed. Run without --no-build or build it first."
  elif ! command -v fastlane &>/dev/null; then
    error "fastlane not found — cannot proceed. Install with: gem install fastlane"
  else
    header "Uploading to Play Console (track: ${UPLOAD_TRACK})…"

    RELEASE_NAME="v${NEW_NAME} (${NEW_CODE})"

    # Write changelog file for this versionCode
    CHANGELOG_DIR="$SCRIPT_DIR/fastlane/metadata/android/en-US/changelogs"
    mkdir -p "$CHANGELOG_DIR"
    CHANGELOG_FILE="$CHANGELOG_DIR/${NEW_CODE}.txt"

    if [[ -n "$RELEASE_NOTES" ]]; then
      # Truncate to 500 chars (Play Console limit)
      printf '%s' "${RELEASE_NOTES:0:500}" > "$CHANGELOG_FILE"
    else
      # Prompt interactively if --notes was not supplied
      echo ""
      echo -e "  ${BLD}What's new in ${RELEASE_NAME}?${RST}  (max 500 chars, blank to skip)"
      read -rp "  Notes: " RELEASE_NOTES
      if [[ -n "$RELEASE_NOTES" ]]; then
        printf '%s' "${RELEASE_NOTES:0:500}" > "$CHANGELOG_FILE"
      else
        rm -f "$CHANGELOG_FILE"  # no file → fastlane skips changelog
      fi
    fi

    if fastlane deploy_internal \
         aab:"$BUNDLE_OUT" \
         track:"$UPLOAD_TRACK"; then
      success "Uploaded to Play Console — ${UPLOAD_TRACK} track as \"${RELEASE_NAME}\""
    else
      error "fastlane upload failed — AAB is still at: $BUNDLE_OUT"
    fi
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
if $DO_UPLOAD; then
  echo -e "  Track: ${BLD}${UPLOAD_TRACK}${RST}"
fi
echo ""
