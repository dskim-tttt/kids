# Development guidance
Use this existing isolated checkout; do not create a Git worktree unless explicitly requested.
Android Java, min API26, SDK35, AGP8.9.1, Gradle8.11.1, JDK17/21. Cloud: source /workspace/.android-tools/activate.sh.
Run ./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug. Increment versionCode and compare APK signer with prior release before publishing a new APK. Do not claim real device GPS/audio/visual behavior from Robolectric tests alone.

# Current product requirements (0.5.0)
The user explicitly requests durable LOCAL collections, visit counts, discoveries, preferences and aggregate ODO/TRIP. This supersedes historical memory-only behavior.
- TripStore stores versioned app-private JSON, protects malformed/future data, and never loses valid cumulative cities/counts via a stale save. Last confirmed city persists, candidate GPS confirmation does not.
- First and return visits require two observations >=60s apart. Reopening/continuing in the same city or unconfirmed border noise must not increment visits or replay effects. A→B→A gives 2 cities, 3 visits/greetings.
- Never expose destructive collection/new-journey reset. Only TRIP can reset; preserve ODO, session distance, stamps and discoveries.
- TravelTracker stores aggregate millimeters only. Raw positions, routes and timestamps stay in RAM. Reject stale/inaccurate/jitter/jump/gap segments; never join paused gaps. Keep tracker across rotation, new launch session counter starts at zero.
- Main direction defaults to moving GPS course (true north), with no direction at rest/low quality. Optional phone compass mode remains magnetic screen-top bearing with rotation remap; unregister it when unused/paused.
- Arrival sound uses MEDIA volume with explicit mute/DND/volume/decoder diagnostics. SoundCatalog has stable selectable IDs. Explicit sample preview can audition with automatic arrival sound off, but still respects phone quiet settings. Stop all playback/animations/timers on pause.
- Collection is 4 columns, visit counts below badges, accessible full city/count labels. Local data survives normal updates/restarts; do not send history to an app server. Geocoder may use the system provider's network route.
- CityIdentity normalizes Korean city aliases and metropolitan fallback, but a province alone is not a city.
- CityLogoCatalog exact keys select bundled images. Current atlas is ORIGINAL app travel art for 12 cities, not government emblems. Official assets need verified HTTPS source/license. Unregistered cities visibly remain pending, never invent an official logo. No runtime image web search/scraping.
- Keep screen awake only while Activity resumed using FLAG_KEEP_SCREEN_ON; clear on pause. No background wake locks or new runtime permissions.
No tracking/analytics SDK, child account, background GPS or raw coordinate logging without an explicit user requirement. Do not reward higher travel speed. Design proposal images are not exact app screenshots.
