# Development guidance
Use this existing isolated checkout; do not create a Git worktree unless explicitly requested.
App: Android Java, API 26+, AGP 8.9.1, Gradle 8.11.1, SDK 35. Use JDK 17 or 21.
Cloud tool state is under /workspace/.android-tools; source /workspace/.android-tools/activate.sh if present.
Run ./gradlew testDebugUnitTest assembleDebug lintDebug. Report physical-device GPS checks separately.
Do not add background location collection, tracking/analytics SDKs, credentials or child accounts without a product requirement.
Do not log coordinates, retain travel history by default, or reward higher travel speeds.
Keep approximate/unknown/stale positions distinct from valid zero speed.
Geocoder is a system network service and does not guarantee offline coverage or precise city boundaries.
Version 0.2.0 implements offline exploration cards and memory-only city stamps. Other feature proposals in docs/playful-learning.md are not implied requirements. Keep discoveries/stamps in memory only, retain on rotation, and clear on explicit new journey/process restart. Confirm a city twice at least 60 seconds apart, reset confirmation on interruption, and never persist coordinates. Run all 49 current tests and lint before publishing a new APK. Increment versionCode and compare signer certificate against the previous APK for updates.

Version 0.3.0 adds a foreground magnetic compass beside the speedometer. It describes the top of the display, not GPS course. Preserve screen rotation remapping, unreliable/missing/stale sensor handling, and lifecycle unsubscribe. Do not use the game rotation vector as a magnetic compass. Real compass behavior needs device testing.

Version 0.4.0 celebrates only newly confirmed stamps with an app-owned city-name badge, a short bundled original chime and optional motion. Keep preview separate from awarding stamps. Honor device mute/DND/volume and animation settings, stop playback/animation/timers on pause, and avoid duplicate celebrations after rotation/resume. Only sound/effects booleans may persist; never persist travel history. The generated badge is not an official municipal emblem. scripts/generate-city-chime.py reproduces the original sound.

Version 0.4.1 celebrates confirmed returns (A→B→A) too. Keep the last confirmed city independent of the unique stamp collection. Continuing in the same city, pause/resume, or an unconfirmed border fluctuation must not replay effects. Each new/return visit still requires two observations >=60s apart; resetJourney clears both confirmation and unique stamps. Revisit text must not imply a new unique stamp was awarded. External city-logo assets are unavailable; do not claim generic badges match them.

Version 0.4.2 keeps the display on only while MainActivity is resumed using FLAG_KEEP_SCREEN_ON, cleared on pause. Preserve all tabs and arrival overlays on the same foreground window. Do not add a background wake lock, extra permission, or change the system-wide screen timeout. Manual power-button locking must remain possible.
