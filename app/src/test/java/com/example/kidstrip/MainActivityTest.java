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
    private TripSession session() { return (TripSession) activity.onRetainNonConfigurationInstance(); }

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
    @Test public void resetRequiresConfirmationAndClearsCardsAndStamps() {
        session().markFound();
        session().observeCity("A", "수원", 0);
        session().observeCity("A", "수원", 60_000);
        click(R.id.nav_stamps);
        click(R.id.new_journey);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        assertEquals(1, session().stampCount());
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, session().stampCount());
        assertEquals(0, session().discoveryCount());
        assertEquals(activity.getString(R.string.found_button),
                ((Button) activity.findViewById(R.id.card_found)).getText().toString());
    }
    @Test public void cancellingResetKeepsTheCurrentJourney() {
        session().markFound();
        click(R.id.nav_stamps);
        click(R.id.new_journey);
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, session().discoveryCount());
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
    @Test public void preferencesRemainAfterFreshActivityButTravelHistoryDoesNot() {
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
        assertEquals(0, session().stampCount());
        assertEquals(0, session().discoveryCount());
    }
}
