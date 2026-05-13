<h1><img src="store/play_store_icon_512.png" width="32" alt="" style="vertical-align:middle;border-radius:20%" /> Loudr</h1>

Volume booster for Android — up to +300%

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-coral.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)](https://developer.android.com)
[![No telemetry](https://img.shields.io/badge/Telemetry-None-blue.svg)](#privacy)
[![CI](https://github.com/drph4nt0m/loudr/actions/workflows/ci.yml/badge.svg)](https://github.com/drph4nt0m/loudr/actions/workflows/ci.yml)

---

## What it does

Loudr amplifies your Android device's audio output beyond the system maximum — up to **+300%** (+20 dB) — without requiring root, without an account, and without an internet connection.

## Features

- **+300% boost** via Android's `LoudnessEnhancer` API
- **Safety limiter** — caps boost at +150% to protect your hearing, with an opt-in Expert Mode for the full range
- **Hearing exposure warnings** — alerts after sustained loud listening
- **Bass boost** — enhanced low-frequency output via `DynamicsProcessing`
- **Auto-boost on headphone connect** — activates automatically when headset or Bluetooth device connects
- **Themes** — Dynamic (Material You), Dark, and AMOLED
- **Quick Settings tile** for one-tap toggle
- **Home screen widgets** — 1×1 toggle and 2×1 boost pill

## Privacy

Loudr collects **zero data**. No internet connection is ever used or required.

- No accounts, no sign-in
- No analytics or crash reporting
- No ads or third-party SDKs that phone home
- All settings stored locally via AndroidX DataStore (`android:allowBackup="false"`)

See [PRIVACY.md](PRIVACY.md) for the full policy.

## Requirements

- Android 8.0 (API 28) or higher
- No root required

## Building

```bash
# Clone
git clone git@github.com:drph4nt0m/loudr.git
cd loudr

# Debug build
./gradlew assembleDebug

# Release bundle (requires signing config in local.properties — see below)
./gradlew bundleRelease

# Or use the release helper script (handles version bump, build, git tag & push)
./release.sh
```

### Signing setup

Create `local.properties` in the project root (already gitignored):

```properties
sdk.dir=/path/to/Android/Sdk

KEYSTORE_PATH=/path/to/your.keystore
KEYSTORE_PASSWORD=your_store_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

Generate a keystore with:

```bash
keytool -genkey -v \
  -keystore ~/loudr-release.keystore \
  -alias loudr \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

## Tech stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Persistence | AndroidX DataStore (Preferences) |
| Audio | `LoudnessEnhancer` + `DynamicsProcessing` |
| Min SDK | API 28 (Android 8.0) |
| Build | Gradle 9 + AGP |

## Project structure

```text
app/src/main/kotlin/me/rhul/loudr/
├── LoudrApp.kt                  # Application entry point (Hilt)
├── data/                        # DataStore repository
├── di/                          # Hilt modules (Hilt + AppScope)
├── engine/                      # Audio boost engine + session monitor
├── safety/                      # Hearing-safety limiter
├── service/                     # Foreground boost service
├── tile/                        # Quick Settings tile
├── ui/
│   ├── main/                    # Main screen + ViewModel
│   └── theme/                   # Colour palette + typography
└── widget/                      # Home screen widgets (toggle + boost pill)

store/                           # Play Store marketing assets (not build inputs)
release.sh                       # Release helper (bump, build, tag, push)
```

## Store assets

Play Store marketing files live in [`store/`](store/):

| File | Purpose |
|------|---------|
| `store/play_store_icon_512.png` | App icon (512×512 px) |
| `store/feature_graphic_1024.png` | Feature graphic (1024×500 px) |
| `store/feature_graphic.svg` | Feature graphic source (editable SVG) |

## License

Loudr is free software, released under the **GNU General Public License v3.0**.

You are free to use, study, modify, and distribute this software under the terms of the GPL-3.0. See [LICENSE](LICENSE) for the full text.
