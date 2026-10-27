package com.example.kidstrip;

/** Magnetic bearing of the top of the display, not GPS travel direction. */
public final class CompassMath {
    private CompassMath() { }
    public static float normalize(float degrees) {
        if (!Float.isFinite(degrees)) return Float.NaN;
        return (degrees % 360f + 360f) % 360f;
    }
    public static float smooth(float previous, float next) {
        next = normalize(next);
        if (!Float.isFinite(next)) return Float.NaN;
        if (!Float.isFinite(previous)) return next;
        float delta = (next - normalize(previous) + 540f) % 360f - 180f;
        return normalize(previous + 0.35f * delta);
    }
    public static int directionIndex(float heading) {
        float normalized = normalize(heading);
        if (!Float.isFinite(normalized)) throw new IllegalArgumentException("Unknown heading");
        return ((int) Math.floor((normalized + 22.5f) / 45f)) % 8;
    }
    public static int roundedDegrees(float heading) {
        float normalized = normalize(heading);
        if (!Float.isFinite(normalized)) throw new IllegalArgumentException("Unknown heading");
        return Math.round(normalized) % 360;
    }
    public static float fromScreenMatrix(float[] matrix) {
        // Screen +Y points toward the top. Matrix rows are east, north, sky.
        float east = matrix[1], north = matrix[4];
        if (!Float.isFinite(east) || !Float.isFinite(north) || Math.hypot(east, north) < 0.25) {
            return Float.NaN; // Top edge nearly vertical: its compass bearing is undefined.
        }
        return normalize((float) Math.toDegrees(Math.atan2(east, north)));
    }
}
