package com.example.kidstrip;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.Surface;
import java.util.function.IntSupplier;

/** Foreground-only sensor subscription, with magnetic fallback and no data storage. */
public final class PhoneCompass implements SensorEventListener {
    public interface Callback {
        void onHeading(float heading);
        void onUnavailable(int message);
    }
    private final SensorManager manager;
    private final IntSupplier rotation;
    private final Callback callback;
    private final float[] matrix = new float[9], screenMatrix = new float[9];
    private final float[] acceleration = new float[3], magnetic = new float[3];
    private Sensor rotationSensor;
    private boolean active, haveAcceleration, haveMagnetic;
    private int magneticAccuracy, lastRotation = -1;
    private float heading = Float.NaN;
    private long lastHeadingAt = -1, lastAccelerationAt = -1, lastMagneticAt = -1;

    public PhoneCompass(SensorManager manager, IntSupplier rotation, Callback callback) {
        this.manager = manager;
        this.rotation = rotation;
        this.callback = callback;
    }
    public void start() {
        if (active) return;
        active = true;
        callback.onUnavailable(R.string.compass_waiting);
        if (manager == null) { unavailable(R.string.compass_unsupported); return; }
        if (registerRotation(Sensor.TYPE_ROTATION_VECTOR)
                || registerRotation(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)) return;
        Sensor accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accelerometer != null && magnetometer != null
                && manager.registerListener(this, accelerometer, 200_000)
                && manager.registerListener(this, magnetometer, 200_000)) return;
        manager.unregisterListener(this);
        unavailable(R.string.compass_unsupported);
    }
    private boolean registerRotation(int type) {
        Sensor sensor = manager.getDefaultSensor(type);
        if (sensor != null && manager.registerListener(this, sensor, 200_000)) {
            rotationSensor = sensor;
            return true;
        }
        return false;
    }
    public void stop() {
        active = false;
        if (manager != null) manager.unregisterListener(this);
        rotationSensor = null;
        haveAcceleration = haveMagnetic = false;
        lastAccelerationAt = lastMagneticAt = -1;
        magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
        lastRotation = -1;
        unavailable(R.string.compass_waiting);
    }
    public void expireStaleHeading() {
        if (active && lastHeadingAt >= 0 && SystemClock.elapsedRealtime() - lastHeadingAt > 5_000) {
            unavailable(R.string.compass_waiting);
        }
    }
    private void unavailable(int message) {
        heading = Float.NaN;
        lastHeadingAt = -1;
        callback.onUnavailable(message);
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!active || event.sensor == null || event.values.length < 3) return;
        for (int i = 0; i < Math.min(event.values.length, 4); i++) {
            if (!Float.isFinite(event.values[i])) { unavailable(R.string.compass_calibrate); return; }
        }
        if (rotationSensor != null) {
            if (event.sensor != rotationSensor) return;
            if (event.accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) {
                unavailable(R.string.compass_calibrate);
                return;
            }
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
        } else {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, acceleration, 0, 3);
                haveAcceleration = true;
                lastAccelerationAt = SystemClock.elapsedRealtime();
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetic, 0, 3);
                magneticAccuracy = event.accuracy;
                haveMagnetic = true;
                lastMagneticAt = SystemClock.elapsedRealtime();
            } else return;
            if (!haveAcceleration || !haveMagnetic) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastAccelerationAt > 5_000 || now - lastMagneticAt > 5_000) {
                unavailable(R.string.compass_waiting);
                return;
            }
            if (magneticAccuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) {
                unavailable(R.string.compass_calibrate);
                return;
            }
            if (!SensorManager.getRotationMatrix(matrix, null, acceleration, magnetic)) {
                unavailable(R.string.compass_flatten);
                return;
            }
        }
        int displayRotation = rotation.getAsInt();
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
        if (displayRotation == Surface.ROTATION_90) { x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; }
        else if (displayRotation == Surface.ROTATION_180) { x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; }
        else if (displayRotation == Surface.ROTATION_270) { x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; }
        if (!SensorManager.remapCoordinateSystem(matrix, x, y, screenMatrix)) {
            unavailable(R.string.compass_waiting);
            return;
        }
        float next = CompassMath.fromScreenMatrix(screenMatrix);
        if (!Float.isFinite(next)) { unavailable(R.string.compass_flatten); return; }
        if (displayRotation != lastRotation) heading = Float.NaN;
        lastRotation = displayRotation;
        heading = CompassMath.smooth(heading, next);
        lastHeadingAt = SystemClock.elapsedRealtime();
        callback.onHeading(heading);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (active && (sensor == rotationSensor || sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                && accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) {
            magneticAccuracy = accuracy;
            unavailable(R.string.compass_calibrate);
        }
    }
}
