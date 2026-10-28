# Validation report — 28 October 2026

## Environment and outcome

Built locally on macOS using Android Studio's bundled JBR, Gradle 9.1.0, Android Gradle Plugin 9.0.1, Kotlin Compose compiler 2.2.10 and SDK 36. Tested on the connected Medium_Phone emulator, Android 16/API 36, 1080 × 2400 pixels, 420 dpi. `adb devices` listed only `emulator-5554`; no Poco F7 or other physical device was attached.

- `assembleDebug`: PASS; APK installed successfully on the emulator.
- `assembleRelease`: PASS; produces an **unsigned** release APK. No production signing key was available.
- `testDebugUnitTest`: **27 passed**, zero failures or skips.
- `connectedDebugAndroidTest`: **16 passed**, zero failures or skips.
- `lintDebug`: PASS with zero errors. Advisory warnings include library update suggestions and intentional synchronous preference commits performed on a worker thread for PIN persistence. No lint baseline suppresses errors.

Sanitized JUnit reports are in `docs/test-results/`. Android Studio/Gradle can regenerate the complete HTML reports under `app/build/reports/`.

## Automated coverage

The 27 pure Kotlin tests cover deliberate rightward unlocking, leftward cutting, reversal rejection, taps, missed scissors, downward shortcut recognition, disabled shortcut behavior, crossing/travel/horizontal constraints, upward and diagonal rejection, cancellation, one-shot consumption, progress clamping, deterministic edge geometry, opening progression, exactly one special clover, configurable placement, same-app activity transitions, app switching, grace periods, disabled switch relock, session clearing, process restart and timeout boundaries.

Six isolated-preference device tests cover salted PIN verifier storage, verification, PIN change, persistent cooldown after five incorrect attempts, settings restoration, refusing protection without a recovery PIN, and deterministic rendering at five cut stages across three display widths.

Ten real-overlay integration tests launch Google Clock and Chrome on the emulator. They confirm:

1. The service detects a protected app, creates the actual overlay, accepts a rightward unlock and relocks after an app switch.
2. A complete wrong cut and Back leave protection in place, followed by reset.
3. Wrong PIN rejection and successful PIN authentication through the overlay keypad.
4. Disabled clover shortcut cannot unlock.
5. Enabled downward five-leaf swipe unlocks.
6. A foreground timeout relocks.
7. Screen-off clears the authenticated session and protection returns after wake.
8. Reconnecting the accessibility service fails closed.
9. Repeated switching between two protected apps leaves exactly one overlay.
10. Returning to an existing authenticated session resumes its original foreground timeout deadline.

The integration tests temporarily modify accessibility settings and test preferences and restore the prior configuration in teardown. Run them on a disposable emulator or dedicated test device. Test screenshots are also copied to the emulator's Download/KawaiiLock-QA folder for retrieval after the test APK is uninstalled.

## Visual inspection

The supplied reference was inspected before implementation. Fourteen screenshots in `screenshots/` show the initial paper, 25/50/75% cuts, complete warning, reset, the actual app after rightward unlock, shortcut configuration, main settings, protected-app selection, appearance settings, wrong-PIN rejection and real protection overlays.

`01` through `05` use debug-only fixed progress to capture exact renderer stages; they are not evidence of live gesture recognition. `real-overlay`, `real-wrong-cut`, `06-reset`, `07-right-unlocked` and `12-pin-rejection` come from the real accessibility-overlay integration tests. Settings images are captured from the real Compose screens. The debug preview's Close preview control is absent from the actual protection overlay.

Inspection confirmed cream grain and fibres, narrow solid green surroundings, charcoal vector scissors, a 17-symbol botanical guide, subtle five-leaf placement, partial text exposed only through the cut, full warning visibility at completion, and restoration of the initial composition. The first pass had insufficient grain; texture contrast was increased. Default purple settings control colors were replaced with the stationery palette. Screenshot tooling was corrected to preserve spaces in Android deep-link arguments.

Tests also exposed and led to fixes for shared Paint state affecting deterministic output, missing final pointer-up positions in cut completion, system-inset handling near PIN entry, and a native-view property name hiding the service's overlay field during PIN completion. All affected tests passed after correction.

## Remaining limitations and unverified scope

- **Poco F7 / HyperOS 3 physical testing remains unavailable.** Confirm accessibility setup, battery-management persistence, OEM navigation, display insets, gestures, haptics, process recovery and extended performance on the actual target phone.
- No production signing or Play Store publication was performed. The debug APK is development-signed; the release APK is unsigned.
- Paper deformation is a deterministic 2D contour effect with edge shading, not a 3D mesh or material simulation. The warning's ink is clean rather than heavily distressed. Authentication removes the overlay immediately; there is no separately retained peel exit effect above the unlocked app.
- Audio is a short optional feedback tone rather than a recorded paper sound. Haptic patterns depend on the Android device.
- Android event delivery permits a brief initial destination frame before an overlay can appear. Accessibility can be disabled by the device owner. There is no claim of system-level security.
- Multi-window, picture-in-picture, unusual secondary displays, very large font scales, screen readers beyond the provided native PIN action/keypad, and long-running OEM resource behavior have not been exhaustively tested.
- Relock sessions are intentionally memory-only; process restoration locks again. Screen-off and reconnect behavior were exercised on the emulator; an actual process-kill scenario is covered by policy tests rather than a physical-device longevity test.
