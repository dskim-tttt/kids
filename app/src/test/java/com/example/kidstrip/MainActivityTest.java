package com.example.kidstrip;

import android.app.AlertDialog;
import android.os.Looper;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void start() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
    }
    @After public void stop() { controller.pause().stop().destroy(); }
    private void click(int id) { activity.findViewById(id).performClick(); }
    private TripSession session() { return activity.tripSession(); }

    @Test public void exploreWorksWithoutLocationAndDoesNotCountDuplicates() {
        assertEquals("—", ((TextView) activity.findViewById(R.id.gps_speed)).getText().toString());
        click(R.id.nav_explore);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_explore).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.page_trip).getVisibility());
        click(R.id.card_found);
        assertEquals(1, session().discoveryCount());
        assertFalse(activity.findViewById(R.id.card_found).isEnabled());
        click(R.id.card_next);
        assertTrue(activity.findViewById(R.id.card_found).isEnabled());
        assertEquals(activity.getString(R.string.card_mountain_title),
                ((TextView) activity.findViewById(R.id.card_title)).getText().toString());
        click(R.id.nav_trip);
        click(R.id.nav_explore);
        assertEquals(1, session().discoveryCount());
    }
    @Test public void onlyTripDistanceCanBeResetAndCollectionSurvives() {
        seedDistance();
        double odo = activity.travelTracker().getOdoMeters();
        assertTrue(odo > 0);
        session().markFound();
        session().observeCity("A", "수원", 0);
        session().observeCity("A", "수원", 60_000);
        assertNull(activity.findViewById(R.id.new_journey));
        click(R.id.reset_trip);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(activity.travelTracker().getTripMeters() > 0);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, activity.travelTracker().getTripMeters(), 0.001);
        assertEquals(odo, activity.travelTracker().getOdoMeters(), 0.001);
        assertEquals(1, session().stampCount());
        assertEquals(1, session().discoveryCount());
    }
    @Test public void cancellingTripResetKeepsTheDistance() {
        seedDistance();
        double trip = activity.travelTracker().getTripMeters();
        click(R.id.reset_trip);
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(trip, activity.travelTracker().getTripMeters(), 0.001);
    }
    private void seedDistance() {
        android.location.Location first = new android.location.Location("gps");
        first.setLatitude(37); first.setLongitude(127); first.setAccuracy(3); first.setSpeed(10);
        first.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        activity.travelTracker().accept(first);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(5));
        android.location.Location second = new android.location.Location(first);
        second.setLatitude(37.00045); second.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        activity.travelTracker().accept(second);
    }
    @Test public void rotationKeepsCardsAndSelectedTabInMemory() {
        click(R.id.nav_explore);
        click(R.id.card_found);
        click(R.id.card_next);
        controller.recreate();
        activity = controller.get();
        assertEquals(1, session().discoveryCount());
        assertEquals(1, session().cardIndex());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_explore).getVisibility());
    }
    @Test public void compassReadoutUsesSensorsAndUnregistersWhenActivityPauses() {
        android.hardware.SensorManager manager = (android.hardware.SensorManager) activity.getSystemService(android.content.Context.SENSOR_SERVICE);
        org.robolectric.shadows.ShadowSensorManager sensors = shadowOf(manager);
        android.hardware.Sensor vector = org.robolectric.shadows.ShadowSensor.newInstance(android.hardware.Sensor.TYPE_ROTATION_VECTOR);
        sensors.addSensor(vector);
        click(R.id.gps_direction_toggle);
        controller.pause().resume();
        assertFalse(sensors.getListeners().isEmpty());
        android.hardware.SensorEvent event = org.robolectric.shadows.ShadowSensorManager.createSensorEvent(4);
        event.sensor = vector;
        event.accuracy = 3;
        event.values[3] = 1;
        sensors.sendSensorEventToListeners(event, vector);
        TextView reading = activity.findViewById(R.id.compass_reading);
        assertEquals(activity.getString(R.string.compass_reading, "북", 0), reading.getText().toString());
        controller.pause();
        assertTrue(sensors.getListeners().isEmpty());
        assertEquals(activity.getString(R.string.compass_waiting), reading.getText().toString());
        controller.resume();
    }
    @Test public void arrivalPreviewIsClearlyMarkedAndDoesNotAwardAStamp() {
        click(R.id.arrival_preview);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        android.widget.LinearLayout panel = activity.findViewById(R.id.arrival_panel);
        assertEquals(activity.getString(R.string.arrival_preview_title), ((TextView) panel.getChildAt(0)).getText().toString());
        assertEquals(0, session().stampCount());
        click(R.id.arrival_close);
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        assertEquals(0, session().stampCount());
    }
    @Test public void arrivalSettingsSurviveRotationAndCanDisableMotion() {
        click(R.id.arrival_sound_toggle);
        click(R.id.arrival_effects_toggle);
        assertFalse(session().arrivalSoundEnabled);
        assertFalse(session().arrivalEffectsEnabled);
        controller.recreate();
        activity = controller.get();
        assertFalse(((android.widget.Switch) activity.findViewById(R.id.arrival_sound_toggle)).isChecked());
        assertFalse(((android.widget.Switch) activity.findViewById(R.id.arrival_effects_toggle)).isChecked());
        click(R.id.arrival_preview);
        assertEquals(1f, activity.findViewById(R.id.arrival_panel).getAlpha(), 0.001f);
        assertEquals(View.GONE, activity.findViewById(R.id.arrival_sparkles).getVisibility());
    }
    @Test public void previewDismissesAutomaticallyAndDoesNotReplayAfterResume() {
        click(R.id.arrival_preview);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5));
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        click(R.id.arrival_preview);
        controller.pause().resume();
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
    }
    @Test public void preferencesAndCollectionRemainAfterFreshActivity() {
        click(R.id.arrival_sound_toggle);
        click(R.id.arrival_effects_toggle);
        session().markFound();
        session().observeCity("A", "수원시", 0);
        session().observeCity("A", "수원시", 60_000);
        controller.pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
        assertFalse(session().arrivalSoundEnabled);
        assertFalse(session().arrivalEffectsEnabled);
        assertEquals(1, session().stampCount());
        assertEquals(1, session().discoveryCount());
    }
    @Test public void gpsTravelDirectionIsDefaultAndStationaryFixHasNoDirection() {
        android.location.Location fix = new android.location.Location("gps");
        fix.setLatitude(37); fix.setLongitude(127); fix.setAccuracy(3); fix.setSpeed(10); fix.setBearing(90);
        fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        activity.onLocationChanged(fix);
        TextView reading = activity.findViewById(R.id.compass_reading);
        assertEquals(activity.getString(R.string.compass_reading, "동", 90), reading.getText().toString());
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
        fix.setSpeed(0); fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        activity.onLocationChanged(fix);
        assertEquals(activity.getString(R.string.gps_direction_waiting), reading.getText().toString());
    }
    @Test public void stampCollectionUsesFourColumnsAndPersistentVisitCounts() {
        for (int i = 0; i < 5; i++) {
            session().observeCity("city" + i, "도시" + i, i * 120_000L);
            session().observeCity("city" + i, "도시" + i, i * 120_000L + 60_000L);
        }
        session().observeCity("city0", "도시0", 600_000);
        session().observeCity("city0", "도시0", 660_000);
        controller.pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).setup(); activity = controller.get();
        click(R.id.nav_stamps);
        android.widget.LinearLayout list = activity.findViewById(R.id.stamp_list);
        assertEquals(2, list.getChildCount());
        assertEquals(4, ((android.widget.LinearLayout) list.getChildAt(0)).getChildCount());
        assertEquals(4, ((android.widget.LinearLayout) list.getChildAt(1)).getChildCount());
        assertEquals(2, session().stamps().get(0).visits);
        android.widget.LinearLayout first = list.findViewWithTag("city0");
        assertEquals(activity.getString(R.string.stamp_visits, 2), ((TextView) first.getChildAt(2)).getText().toString());
    }
    @Test public void soundSampleChoiceIsKeptAcrossReopen() {
        android.widget.Spinner picker = activity.findViewById(R.id.sound_samples);
        picker.setSelection(2);
        shadowOf(Looper.getMainLooper()).idle();
        controller.pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).setup(); activity = controller.get();
        assertEquals(2, ((android.widget.Spinner) activity.findViewById(R.id.sound_samples)).getSelectedItemPosition());
    }
    @Test public void staleDisplayedGpsDoesNotBridgeItsGapIntoDistance() {
        android.location.Location fix = new android.location.Location("gps");
        fix.setLatitude(37); fix.setLongitude(127); fix.setAccuracy(3); fix.setSpeed(10);
        fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos()); activity.onLocationChanged(fix);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(5));
        fix.setLatitude(37.00045); fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos()); activity.onLocationChanged(fix);
        double before = activity.travelTracker().getOdoMeters(); assertTrue(before > 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(11));
        fix.setLatitude(37.00145); fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos()); activity.onLocationChanged(fix);
        assertEquals(before, activity.travelTracker().getOdoMeters(), 0.001);
    }
    private android.media.AudioManager audibleDevice() {
        android.media.AudioManager audio = (android.media.AudioManager) activity.getSystemService(android.content.Context.AUDIO_SERVICE);
        android.app.NotificationManager notifications = (android.app.NotificationManager) activity.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        shadowOf(notifications).setNotificationPolicyAccessGranted(true);
        notifications.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
        audio.setRingerMode(android.media.AudioManager.RINGER_MODE_NORMAL);
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0);
        return audio;
    }
    @Test public void silenceDiagnosisClearsWhenMediaVolumeIsRaised() {
        android.media.AudioManager audio = audibleDevice();
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0);
        click(R.id.sound_sample_preview);
        TextView status = activity.findViewById(R.id.audio_status);
        assertEquals(activity.getString(R.string.audio_reason_media_volume), status.getText().toString());
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals(activity.getString(R.string.audio_waits_for_city), status.getText().toString());
    }
    @Test public void asynchronousAudioFailureIsShownInDiagnostics() {
        audibleDevice();
        java.util.concurrent.atomic.AtomicReference<android.media.MediaPlayer> created = new java.util.concurrent.atomic.AtomicReference<>();
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener((player, shadow) -> created.set(player));
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(source -> new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(750, 0));
        click(R.id.sound_sample_preview);
        assertNotNull(created.get());
        shadowOf(created.get()).invokeErrorListener(1, 1);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals(activity.getString(R.string.audio_reason_playback), ((TextView) activity.findViewById(R.id.audio_status)).getText().toString());
    }
}
