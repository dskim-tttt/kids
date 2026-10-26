package com.example.kidstrip;

/** GPS speed is meters/second. Unknown, inaccurate or stale fixes must never appear as zero. */
public final class SpeedPolicy {
    private SpeedPolicy() {}
    public static Integer kmh(boolean hasSpeed, float metersPerSecond, float accuracyMeters,
                              boolean hasSpeedAccuracy, float speedAccuracy, long ageMillis) {
        if (!hasSpeed || !Float.isFinite(metersPerSecond) || metersPerSecond < 0
                || !Float.isFinite(accuracyMeters) || accuracyMeters > 50 || accuracyMeters < 0
                || ageMillis < 0 || ageMillis > 10_000
                || (hasSpeedAccuracy && (!Float.isFinite(speedAccuracy) || speedAccuracy > 3 || speedAccuracy < 0))) {
            return null;
        }
        return Math.round(metersPerSecond * 3.6f);
    }
}
