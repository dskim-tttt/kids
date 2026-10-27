# Development guidance
Use this existing isolated checkout; do not create a Git worktree unless explicitly requested.
App: Android Java, API 26+, AGP 8.9.1, Gradle 8.11.1, SDK 35. Use JDK 17 or 21.
Cloud tool state is under /workspace/.android-tools; source /workspace/.android-tools/activate.sh if present.
Run ./gradlew testDebugUnitTest assembleDebug lintDebug. Report physical-device GPS checks separately.
Do not add background location collection, tracking/analytics SDKs, credentials or child accounts without a product requirement.
Do not log coordinates, retain travel history by default, or reward higher travel speeds.
Keep approximate/unknown/stale positions distinct from valid zero speed.
Geocoder is a system network service and does not guarantee offline coverage or precise city boundaries.
Version 0.2.0 implements offline exploration cards and memory-only city stamps. Other feature proposals in docs/playful-learning.md are not implied requirements. Keep discoveries/stamps in memory only, retain on rotation, and clear on explicit new journey/process restart. Confirm a city twice at least 60 seconds apart, reset confirmation on interruption, and never persist coordinates. Run all 19 current tests and lint before publishing a new APK. Increment versionCode and compare signer certificate against the previous APK for updates.
