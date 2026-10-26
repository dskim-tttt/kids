# Development guidance
Use this existing isolated checkout; do not create a Git worktree unless explicitly requested.
App: Android Java, API 26+, AGP 8.9.1, Gradle 8.11.1, SDK 35. Use JDK 17 or 21.
Cloud tool state is under /workspace/.android-tools; source /workspace/.android-tools/activate.sh if present.
Run ./gradlew testDebugUnitTest assembleDebug lintDebug. Report physical-device GPS checks separately.
Do not add background location collection, tracking/analytics SDKs, credentials or child accounts without a product requirement.
Do not log coordinates, retain travel history by default, or reward higher travel speeds.
Keep approximate/unknown/stale positions distinct from valid zero speed.
Geocoder is a system network service and does not guarantee offline coverage or precise city boundaries.
Feature proposals in docs/playful-learning.md are not implemented requirements.
