# KawaiiLock v0.3.0 — Stationery rebuild

Complete Android Studio source is now included. This rebuild replaces the old concept with cream paper, botanical cutting guides and small articulated charcoal scissors.

- Event-driven accessibility app protection with multi-app selection.
- Leftward cut exposes the underlying warning and stays locked; partial cuts reset smoothly.
- Rightward secret unlock and optional five-leaf downward shortcut.
- Secure fallback PIN setup, confirmation, verification and change, with persistent failed-attempt cooldowns.
- Persistent appearance, warning, shortcut, relock and feedback settings.
- Unit tests, Android instrumentation tests, emulator screenshots, setup and recovery documentation.

The attached debug APK is installable and development-signed. The unsigned release APK is for signing with your own key; it is not installable as supplied. There are no bundled production signing credentials.

The existing `v0.1.0` and `0.2.0` releases are preserved. Version 0.3.0 avoids confusing this source rebuild with the existing 0.2.0 binary release.

Poco F7 / Android 16 / HyperOS 3 physical-device validation remains outstanding because no physical device was connected. Review docs/QA.md and README.md for the scope of testing, recovery and platform limitations. A third-party accessibility overlay is a casual privacy barrier; users can disable it in system settings.
