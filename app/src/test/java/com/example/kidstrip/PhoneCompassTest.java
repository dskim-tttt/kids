package com.example.kidstrip;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.view.Surface;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSensor;
import org.robolectric.shadows.ShadowSensorManager;
import org.robolectric.shadows.ShadowSystemClock;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PhoneCompassTest {
    private SensorManager manager;
    private ShadowSensorManager sensors;
    private PhoneCompass compass;
    private int rotation, message;
    private float heading;
    @Before public void setup() {
        manager = (SensorManager) RuntimeEnvironment.getApplication().getSystemService(Context.SENSOR_SERVICE);
        sensors = shadowOf(manager);
        heading = Float.NaN;
        compass = new PhoneCompass(manager, () -> rotation, new PhoneCompass.Callback() {
            @Override public void onHeading(float value) { heading = value; message = 0; }
            @Override public void onUnavailable(int value) { heading = Float.NaN; message = value; }
        });
    }
    @After public void teardown() { compass.stop(); }
    private Sensor sensor(int type) {
        Sensor sensor = ShadowSensor.newInstance(type);
        sensors.addSensor(sensor);
        return sensor;
    }
    private void send(Sensor sensor, int accuracy, float... values) {
        SensorEvent event = ShadowSensorManager.createSensorEvent(values.length);
        event.sensor = sensor;
        event.accuracy = accuracy;
        System.arraycopy(values, 0, event.values, 0, values.length);
        sensors.sendSensorEventToListeners(event, sensor);
    }
    private void north(Sensor sensor) { send(sensor, 3, 0, 0, 0, 1); }
    @Test public void missingSensorsAndGameOnlySensorsDoNotInventCompassReadings() {
        sensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        compass.start();
        assertEquals(R.string.compass_unsupported, message);
        assertFalse(sensors.hasListener(compass));
        assertTrue(Float.isNaN(heading));
    }
    @Test public void magneticRotationAndAllDisplayRotationsAreMappedToScreenTop() {
        Sensor vector = sensor(Sensor.TYPE_ROTATION_VECTOR);
        compass.start();
        assertTrue(sensors.hasListener(compass, vector));
        for (int i = 0; i < 4; i++) {
            rotation = i;
            north(vector);
            assertEquals(i * 90f, heading, 0.01f);
        }
    }
    @Test public void geomagneticRotationIsUsedWhenTheGyroscopeVectorIsAbsent() {
        Sensor vector = sensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        compass.start();
        north(vector);
        assertEquals(0f, heading, 0.01f);
    }
    @Test public void unreliableAccuracyVerticalPhoneAndMissingEventsClearOldHeading() {
        Sensor vector = sensor(Sensor.TYPE_ROTATION_VECTOR);
        compass.start(); north(vector);
        send(vector, 0, 0, 0, 0, 1);
        assertEquals(R.string.compass_calibrate, message);
        send(vector, 3, 0.70710677f, 0, 0, 0.70710677f);
        assertEquals(R.string.compass_flatten, message);
        north(vector);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6));
        compass.expireStaleHeading();
        assertEquals(R.string.compass_waiting, message);
        assertTrue(Float.isNaN(heading));
    }
    @Test public void fallbackNeedsBothFreshSensorsAndStopsOnPause() {
        Sensor gravity = sensor(Sensor.TYPE_ACCELEROMETER);
        Sensor field = sensor(Sensor.TYPE_MAGNETIC_FIELD);
        compass.start();
        send(gravity, 3, 0, 0, 9.81f);
        assertTrue(Float.isNaN(heading));
        send(field, 3, 0, 50, 0);
        assertEquals(0f, heading, 0.01f);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6));
        send(gravity, 3, 0, 0, 9.81f);
        assertTrue(Float.isNaN(heading));
        compass.stop();
        assertFalse(sensors.hasListener(compass));
        // No callbacks or old field values may leak into the next foreground session.
        compass.start();
        send(gravity, 3, 0, 0, 9.81f);
        assertTrue(Float.isNaN(heading));
        send(field, 3, 0, 50, 0);
        assertEquals(0f, heading, 0.01f);
    }
    @Test public void listenerRegistrationFailureLeavesNoLeakedSubscription() {
        sensor(Sensor.TYPE_ROTATION_VECTOR);
        sensor(Sensor.TYPE_ACCELEROMETER);
        sensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensors.setForceListenersToFail(true);
        compass.start();
        assertEquals(R.string.compass_unsupported, message);
        assertFalse(sensors.hasListener(compass));
    }
    @Test public void sensorAccuracyCallbackRemovesAPreviouslyValidDirection() {
        Sensor vector = sensor(Sensor.TYPE_ROTATION_VECTOR);
        compass.start(); north(vector);
        compass.onAccuracyChanged(vector, SensorManager.SENSOR_STATUS_UNRELIABLE);
        assertTrue(Float.isNaN(heading));
        assertEquals(R.string.compass_calibrate, message);
    }
}
