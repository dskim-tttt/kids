package com.example.kidstrip;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;

/** Foreground GPS metrics. Only aggregate ODO/TRIP millimeters are stored, never a route. */
public final class TravelTracker {
    static final String PREFERENCES = "travel_metrics_v1";
    private static final long MAX_FIX_AGE_NANOS = 10_000_000_000L;
    private static final double MAX_SEGMENT_SECONDS = 15;
    private static final float MAX_ACCURACY_METERS = 30;
    private static final float MIN_DISTANCE_SPEED_MPS = 0.8f;
    private static final float MIN_COURSE_SPEED_MPS = 1.5f;
    private static final float MAX_SPEED_MPS = 100; // Allows high-speed trains, rejects large GPS jumps.
    private static final long MAX_COUNTER_MM = 1_000_000_000_000_000L;
    private final SharedPreferences preferences;
    private double odoMeters, tripMeters, sessionMeters;
    private Location anchor;
    private Float course;
    private long courseAtNanos = -1;
    private long lastFixAtNanos = -1;

    public TravelTracker(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        odoMeters = readMillimeters("odo_mm") / 1000d;
        tripMeters = Math.min(odoMeters, readMillimeters("trip_mm") / 1000d);
    }
    private long readMillimeters(String key) {
        try {
            long value = preferences.getLong(key, 0);
            return value >= 0 && value <= MAX_COUNTER_MM ? value : 0;
        } catch (ClassCastException ignored) {
            return 0;
        }
    }
    public double getOdoMeters() { return odoMeters; }
    public double getTripMeters() { return tripMeters; }
    public double getSessionMeters() { return sessionMeters; }
    public Float getCourseDegrees() {
        if (course != null && SystemClock.elapsedRealtimeNanos() - courseAtNanos > MAX_FIX_AGE_NANOS) {
            course = null;
            courseAtNanos = -1;
        }
        return course;
    }
    /** Discard a paused/stale segment without resetting either distance counter. */
    public void clearAnchor() {
        anchor = null;
        lastFixAtNanos = -1;
        course = null;
        courseAtNanos = -1;
    }
    public void resetTrip() {
        tripMeters = 0;
        save();
    }
    /** Called for live GPS fixes. A copy of only one anchor remains in RAM. */
    public void accept(Location fix) {
        long now = SystemClock.elapsedRealtimeNanos();
        if (!validPosition(fix, now) || !reliableSpeed(fix)) {
            clearAnchor();
            return;
        }
        if (fix.getElapsedRealtimeNanos() <= lastFixAtNanos) return;
        lastFixAtNanos = fix.getElapsedRealtimeNanos();
        updateCourse(fix);
        if (fix.getSpeed() < MIN_DISTANCE_SPEED_MPS) {
            anchor = new Location(fix);
            return;
        }
        if (anchor == null) {
            anchor = new Location(fix);
            return;
        }
        double seconds = (fix.getElapsedRealtimeNanos() - anchor.getElapsedRealtimeNanos()) / 1_000_000_000d;
        if (seconds <= 0) return; // Duplicate/out-of-order fixes cannot add distance.
        if (seconds > MAX_SEGMENT_SECONDS) {
            anchor = new Location(fix);
            return;
        }
        double distance = anchor.distanceTo(fix);
        double maximum = Math.max(10, Math.max(anchor.getSpeed(), fix.getSpeed()) * seconds * 2 + 5);
        if (!Double.isFinite(distance) || distance / seconds > MAX_SPEED_MPS || distance > maximum) {
            // Start afresh after a jump; never count the return leg of a bad point either.
            course = null;
            courseAtNanos = -1;
            anchor = new Location(fix);
            return;
        }
        double uncertainty = Math.max(2, (anchor.getAccuracy() + fix.getAccuracy()) * 0.5);
        if (distance < uncertainty) return; // Accumulate real movement beyond GPS noise before counting.
        odoMeters += distance;
        tripMeters += distance;
        sessionMeters += distance;
        anchor = new Location(fix);
        save(); // Every accepted segment checkpoints aggregates; no city/coordinate payload is written.
    }
    private static boolean validPosition(Location fix, long now) {
        if (fix == null || !fix.hasAccuracy()) return false;
        long timestamp = fix.getElapsedRealtimeNanos();
        return timestamp >= 0 && timestamp <= now && now - timestamp <= MAX_FIX_AGE_NANOS
                && Double.isFinite(fix.getLatitude()) && Math.abs(fix.getLatitude()) <= 90
                && Double.isFinite(fix.getLongitude()) && Math.abs(fix.getLongitude()) <= 180
                && Float.isFinite(fix.getAccuracy()) && fix.getAccuracy() >= 0
                && fix.getAccuracy() <= MAX_ACCURACY_METERS;
    }
    private static boolean reliableSpeed(Location fix) {
        return fix.hasSpeed() && Float.isFinite(fix.getSpeed()) && fix.getSpeed() >= 0
                && fix.getSpeed() <= MAX_SPEED_MPS
                && (!fix.hasSpeedAccuracy() || (Float.isFinite(fix.getSpeedAccuracyMetersPerSecond())
                && fix.getSpeedAccuracyMetersPerSecond() >= 0 && fix.getSpeedAccuracyMetersPerSecond() <= 3));
    }
    private void updateCourse(Location fix) {
        float minimumSpeed = fix.hasSpeedAccuracy()
                ? Math.max(MIN_COURSE_SPEED_MPS, fix.getSpeedAccuracyMetersPerSecond() * 2)
                : MIN_COURSE_SPEED_MPS;
        boolean preciseBearing = !fix.hasBearingAccuracy() || (Float.isFinite(fix.getBearingAccuracyDegrees())
                && fix.getBearingAccuracyDegrees() >= 0 && fix.getBearingAccuracyDegrees() <= 35);
        if (fix.getSpeed() >= minimumSpeed && fix.hasBearing() && Float.isFinite(fix.getBearing()) && preciseBearing) {
            course = CompassMath.normalize(fix.getBearing());
            courseAtNanos = fix.getElapsedRealtimeNanos();
        } else {
            course = null;
            courseAtNanos = -1;
        }
    }
    public void save() {
        preferences.edit().putInt("schema_version", 1)
                .putLong("odo_mm", Math.round(odoMeters * 1000))
                .putLong("trip_mm", Math.round(tripMeters * 1000)).apply();
    }
}
