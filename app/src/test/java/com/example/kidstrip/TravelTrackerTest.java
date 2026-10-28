package com.example.kidstrip;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TravelTrackerTest {
    private Context context;
    private TravelTracker tracker;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        tracker = new TravelTracker(context);
    }
    private Location fix(double eastMeters, float speed, float bearing) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(0);
        location.setLongitude(eastMeters / 111319.49079327357);
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        location.setAccuracy(3);
        location.setSpeed(speed);
        location.setSpeedAccuracyMetersPerSecond(0.5f);
        location.setBearing(bearing);
        location.setBearingAccuracyDegrees(5);
        return location;
    }
    private void advance(int seconds) { ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds)); }
    private void moveTenMeters() {
        tracker.accept(fix(0, 10, 90));
        advance(1);
        tracker.accept(fix(10, 10, 90));
    }
    @Test public void countsRealMovementAndPersistsOnlyAggregateCounters() {
        moveTenMeters();
        advance(1);
        tracker.accept(fix(20, 10, 90));
        assertEquals(20, tracker.getOdoMeters(), 0.03);
        assertEquals(20, tracker.getTripMeters(), 0.03);
        assertEquals(20, tracker.getSessionMeters(), 0.03);
        TravelTracker restored = new TravelTracker(context);
        assertEquals(20, restored.getOdoMeters(), 0.03);
        assertEquals(20, restored.getTripMeters(), 0.03);
        assertEquals(0, restored.getSessionMeters(), 0);
        assertNull(restored.getCourseDegrees());
        SharedPreferences preferences = context.getSharedPreferences(TravelTracker.PREFERENCES, Context.MODE_PRIVATE);
        assertEquals(new HashSet<>(Arrays.asList("schema_version", "odo_mm", "trip_mm")), preferences.getAll().keySet());
    }
    @Test public void resetTripPreservesLifetimeAndCurrentLaunchDistances() {
        moveTenMeters();
        tracker.resetTrip();
        assertEquals(10, tracker.getOdoMeters(), 0.03);
        assertEquals(10, tracker.getSessionMeters(), 0.03);
        assertEquals(0, tracker.getTripMeters(), 0);
        assertEquals(0, new TravelTracker(context).getTripMeters(), 0);
        advance(1);
        tracker.accept(fix(20, 10, 90));
        assertEquals(20, tracker.getOdoMeters(), 0.03);
        assertEquals(10, tracker.getTripMeters(), 0.03);
    }
    @Test public void stationaryPositionNoiseNeverAddsDistanceOrInventsCourse() {
        tracker.accept(fix(0, 0, 90));
        for (int i = 0; i < 8; i++) {
            advance(1);
            tracker.accept(fix(i % 2 == 0 ? 15 : -15, 0, 90));
        }
        assertEquals(0, tracker.getOdoMeters(), 0);
        assertNull(tracker.getCourseDegrees());
    }
    @Test public void rejectsInaccurateMissingSpeedStaleAndFutureFixes() {
        tracker.accept(fix(0, 10, 90));
        advance(1);
        Location badAccuracy = fix(1000, 10, 90);
        badAccuracy.setAccuracy(90);
        tracker.accept(badAccuracy);
        advance(1);
        tracker.accept(fix(20, 10, 90)); // First good point after the invalid fix is an anchor only.
        assertEquals(0, tracker.getOdoMeters(), 0);
        Location noSpeed = fix(1000, 10, 90);
        noSpeed.removeSpeed();
        advance(1); tracker.accept(noSpeed);
        assertNull(tracker.getCourseDegrees());
        Location stale = fix(1000, 10, 90);
        advance(11); tracker.accept(stale);
        assertNull(tracker.getCourseDegrees());
        Location future = fix(1000, 10, 90);
        future.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos() + 1_000_000_000L);
        tracker.accept(future);
        assertEquals(0, tracker.getOdoMeters(), 0);
    }
    @Test public void implausibleTeleportAndItsReturnLegAreNotCounted() {
        tracker.accept(fix(0, 10, 90));
        advance(1); tracker.accept(fix(1000, 10, 90));
        advance(1); tracker.accept(fix(20, 10, 90));
        assertEquals(0, tracker.getOdoMeters(), 0);
        advance(1); tracker.accept(fix(30, 10, 90));
        assertEquals(10, tracker.getOdoMeters(), 0.03);
    }
    @Test public void aLongSignalGapOrPauseDoesNotJoinUnobservedTravel() {
        moveTenMeters();
        advance(16); tracker.accept(fix(170, 10, 90));
        assertEquals(10, tracker.getOdoMeters(), 0.03);
        advance(1); tracker.accept(fix(180, 10, 90));
        assertEquals(20, tracker.getOdoMeters(), 0.03);
        tracker.clearAnchor();
        assertNull(tracker.getCourseDegrees());
        advance(1); tracker.accept(fix(200, 10, 90));
        assertEquals(20, tracker.getOdoMeters(), 0.03);
    }
    @Test public void courseUsesGpsBearingAndExpiresWithoutNewFixes() {
        tracker.accept(fix(0, 10, 90));
        assertEquals(90, tracker.getCourseDegrees(), 0.001f);
        advance(11);
        assertNull(tracker.getCourseDegrees());
        tracker.accept(fix(110, 10, 270));
        assertEquals(270, tracker.getCourseDegrees(), 0.001f);
    }
    @Test public void courseRequiresMovementAndReliableBearing() {
        tracker.accept(fix(0, 1, 90));
        assertNull(tracker.getCourseDegrees());
        advance(1);
        Location missing = fix(1, 10, 90); missing.removeBearing(); tracker.accept(missing);
        assertNull(tracker.getCourseDegrees());
        advance(1);
        Location inaccurate = fix(11, 10, 90); inaccurate.setBearingAccuracyDegrees(70); tracker.accept(inaccurate);
        assertNull(tracker.getCourseDegrees());
        advance(1);
        Location uncertainSpeed = fix(21, 2, 90); uncertainSpeed.setSpeedAccuracyMetersPerSecond(2); tracker.accept(uncertainSpeed);
        assertNull(tracker.getCourseDegrees());
    }
    @Test public void duplicateAndOutOfOrderFixesDoNotRecountOrReplaceTheCourse() {
        Location first = fix(0, 10, 90);
        tracker.accept(first);
        advance(1);
        Location second = fix(10, 10, 90);
        tracker.accept(second);
        Location duplicate = new Location(second); duplicate.setLongitude(1000 / 111319.49079327357);
        duplicate.setBearing(270);
        tracker.accept(duplicate);
        tracker.accept(first);
        assertEquals(10, tracker.getOdoMeters(), 0.03);
        assertEquals(90, tracker.getCourseDegrees(), 0.001f);
    }
    @Test public void slowGenuineMovementAccumulatesPastPositionUncertainty() {
        tracker.accept(fix(0, 1, 90));
        advance(1); tracker.accept(fix(1, 1, 90));
        advance(1); tracker.accept(fix(2, 1, 90));
        assertEquals(0, tracker.getOdoMeters(), 0);
        advance(1); tracker.accept(fix(3.1, 1, 90));
        assertEquals(3.1, tracker.getOdoMeters(), 0.03);
    }
    @Test public void malformedLocalCountersDoNotCrashOrExposeNegativeDistances() {
        context.getSharedPreferences(TravelTracker.PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString("odo_mm", "bad").putLong("trip_mm", -5).apply();
        TravelTracker restored = new TravelTracker(context);
        assertEquals(0, restored.getOdoMeters(), 0);
        assertEquals(0, restored.getTripMeters(), 0);
    }
}
