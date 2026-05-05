# Loudr Privacy Policy

> **Effective date:** 2026-05-04
> **App version:** 1.0.0+

## The Short Version

**Loudr does not collect, store, or transmit any data about you or your device.**

No accounts. No sign-in. No telemetry. No analytics. No ads. No internet connection required — or used — ever.

---

## What Data Loudr Accesses

| Data | Purpose | Leaves your device? |
| --- | --- | --- |
| Audio session IDs | Attach the volume boost effect | **Never** |
| Headset connection state | Auto-boost / detach on connect or disconnect | **Never** |
| Installed app list | Per-app boost profiles *(only when you open that screen)* | **Never** |
| Boost level & settings | Stored locally in encrypted DataStore | **Never** |

Loudr stores your settings (boost level, enabled streams, per-app profiles, theme choice) in your device's private app storage using Android DataStore. This data is **never backed up to any cloud service** (`android:allowBackup="false"`).

---

## Permissions Explained

Loudr uses the minimum permissions necessary. Here is every permission and why it exists:

| Permission | Why |
| --- | --- |
| `MODIFY_AUDIO_SETTINGS` | Required to attach the volume boost effect to an audio session |
| `FOREGROUND_SERVICE` | Keeps the boost active when you switch apps |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required Android declaration for the service type |
| `POST_NOTIFICATIONS` *(optional, API 33+)* | Show the persistent boost notification — requested only when you start the service |
| `RECORD_AUDIO` *(optional)* | Power the waveform visualizer — requested only if you enable it in Settings. Audio is **never recorded or stored**. |
| `SYSTEM_ALERT_WINDOW` *(optional)* | Draw the floating boost overlay — requested only if you enable it in Settings |
| `QUERY_ALL_PACKAGES` *(optional)* | Show your installed apps in the Per-App Profiles screen — requested only when you open that screen |

**We will never request:** `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_PHONE_STATE`, `READ_CONTACTS`, `CAMERA`, `LOCATION`, or any other permission not listed above.

---

## Third-Party Libraries

Loudr uses the following open-source libraries. **None of them transmit data.**

| Library | Purpose | Network access? |
| --- | --- | --- |
| Jetpack Compose | UI framework | No |
| Hilt (Dagger) | Dependency injection | No |
| AndroidX DataStore | Settings persistence | No |
| Protocol Buffers (lite) | DataStore serialisation | No |

Every dependency is audited in CI to verify no `INTERNET` permission is added transitively.

---

## Open Source

Loudr is fully open source under the **GPL-3.0 license**. You can read every line of code, verify our privacy claims, and build the app yourself.

**Source code:** <https://github.com/loudr-app/loudr>

---

## Contact

Questions or privacy concerns? Reach out via any of the following:

- **Email:** <hi@rhul.me>
- **GitHub:** Open an Issue or Discussion at <https://github.com/drph4nt0m/loudr>
