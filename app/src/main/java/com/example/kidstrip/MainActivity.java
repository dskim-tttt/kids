package com.example.kidstrip;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements LocationListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean legacyGeocoderBusy = new java.util.concurrent.atomic.AtomicBoolean();
    private final ExecutorService geocoding = Executors.newSingleThreadExecutor();
    private LocationManager manager;
    private TextView speed, city, status;
    private Location latest, cityAnchor;
    private boolean active, listening, resolving;
    private long lastCityRequest;
    private int epoch, cityRequestId;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!active) return;
            updateSpeed();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.setBackgroundColor(Color.rgb(246,243,233));
        content.addView(label("우리의 여행", 24));
        content.addView(label("지금 얼마나 빠르게 가고 있을까?", 18));
        speed = label("—", 88);
        content.addView(speed);
        content.addView(label("km/h", 24));
        content.addView(label("지금 있는 도시", 18));
        city = label("위치를 기다리고 있어요", 32);
        content.addView(city);
        status = label("GPS를 준비하고 있어요", 16);
        content.addView(status);
        Button permission = new Button(this);
        permission.setText("위치 사용 허용");
        permission.setOnClickListener(v -> requestLocation());
        content.addView(permission);
        Button settings = new Button(this);
        settings.setText("위치 설정 열기");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        content.addView(settings);
        content.addView(label("보호자와 함께 설정해요. 화면을 보는 동안만 위치를 사용해요.\n위치와 이동 기록을 앱에 저장하지 않아요.\n도시 이름 조회에는 기기의 위치 서비스와 인터넷이 사용될 수 있어요.", 14));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        // Android 15 edge-to-edge: keep all content inside system bars.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(scroll);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(23,65,62));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(12), 0, dp(12));
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onResume() {
        super.onResume();
        active = true;
        startGps();
        handler.post(ticker);
    }
    @Override public void onPause() {
        active = false;
        epoch++;
        resolving = false;
        cityRequestId++;
        cityAnchor = null;
        lastCityRequest = 0;
        latest = null;
        if (listening) manager.removeUpdates(this);
        listening = false;
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
    @Override public void onDestroy() {
        geocoding.shutdownNow();
        super.onDestroy();
    }
    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startGps();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && active) startGps();
    }
    private void startGps() {
        if (listening || !active) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status.setText("속도를 보려면 ‘위치 사용 허용’에서 정확한 위치를 허용해 주세요");
            return;
        }
        if (!manager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
            status.setText(R.string.gps_unsupported);
            return;
        }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            status.setText("휴대폰 위치 설정을 켜 주세요");
            return;
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
            listening = true;
            status.setText(R.string.gps_waiting_window);
        } catch (SecurityException ex) {
            status.setText("위치 권한이 필요해요");
        } catch (IllegalArgumentException ex) {
            status.setText(R.string.gps_unavailable);
        }
    }
    @Override public void onLocationChanged(Location location) {
        if (!active) return;
        latest = new Location(location);
        updateSpeed();
        long age = (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000;
        if (age >= 0 && age <= 10_000 && location.hasAccuracy() && location.getAccuracy() <= 100) {
            resolveCity(location);
        }
    }
    @Override public void onProviderDisabled(String provider) {
        latest = null;
        speed.setText("—");
        city.setText("위치를 기다리고 있어요");
        status.setText(R.string.gps_disabled);
    }
    @Override public void onProviderEnabled(String provider) {
        status.setText(R.string.gps_waiting);
    }
    @Override public void onStatusChanged(String provider, int state, Bundle extras) { }

    private void updateSpeed() {
        if (latest == null) return;
        long age = (SystemClock.elapsedRealtimeNanos() - latest.getElapsedRealtimeNanos()) / 1_000_000;
        Integer value = SpeedPolicy.kmh(latest.hasSpeed(), latest.getSpeed(),
                latest.hasAccuracy() ? latest.getAccuracy() : Float.POSITIVE_INFINITY,
                latest.hasSpeedAccuracy(), latest.hasSpeedAccuracy() ? latest.getSpeedAccuracyMetersPerSecond() : 0, age);
        speed.setText(value == null ? "—" : String.valueOf(value));
        status.setText(value == null ? "GPS 속도를 확인하고 있어요 · 신호가 약할 수 있어요" : "GPS로 측정한 대략적인 속도예요");
        if (age > 10_000) {
            city.setText("위치를 다시 확인하고 있어요");
            cityAnchor = null;
            lastCityRequest = 0;
        }
    }
    private void resolveCity(Location location) {
        long now = SystemClock.elapsedRealtime();
        if (resolving || (lastCityRequest > 0 && now - lastCityRequest < 60_000)) return;
        if (cityAnchor != null && cityAnchor.distanceTo(location) < 1000 && now - lastCityRequest < 300_000) return;
        if (!Geocoder.isPresent()) {
            city.setText("이 기기에서는 도시 조회를 지원하지 않아요");
            return;
        }
        resolving = true;
        lastCityRequest = now;
        final int request = ++cityRequestId;
        final int generation = epoch;
        final Location anchor = new Location(location);
        Geocoder coder = new Geocoder(this, Locale.KOREAN);
        handler.postDelayed(() -> finishCity(request, generation, anchor, null), 15_000);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                coder.getFromLocation(location.getLatitude(), location.getLongitude(), 1, new Geocoder.GeocodeListener() {
                    @Override public void onGeocode(List<Address> addresses) {
                        handler.post(() -> finishCity(request, generation, anchor, addresses));
                    }
                    @Override public void onError(String error) {
                        handler.post(() -> finishCity(request, generation, anchor, null));
                    }
                });
            } catch (RuntimeException ex) {
                finishCity(request, generation, anchor, null);
            }
        } else {
            if (!legacyGeocoderBusy.compareAndSet(false, true)) {
                finishCity(request, generation, anchor, null);
                return;
            }
            geocoding.execute(() -> {
                List<Address> addresses = null;
                try { addresses = coder.getFromLocation(location.getLatitude(), location.getLongitude(), 1); }
                catch (Exception ignored) { }
                finally { legacyGeocoderBusy.set(false); }
                final List<Address> result = addresses;
                handler.post(() -> finishCity(request, generation, anchor, result));
            });
        }
    }
    private void finishCity(int request, int generation, Location anchor, List<Address> addresses) {
        if (!active || generation != epoch || request != cityRequestId || !resolving) return;
        resolving = false;
        if (latest == null || (SystemClock.elapsedRealtimeNanos() - latest.getElapsedRealtimeNanos()) > 10_000_000_000L) {
            city.setText("위치를 다시 확인하고 있어요");
            return;
        }
        String name = null;
        if (addresses != null && !addresses.isEmpty()) {
            Address address = addresses.get(0);
            name = address.getLocality();
            if (name == null || name.trim().isEmpty()) name = address.getSubAdminArea();
            if (name == null || name.trim().isEmpty()) name = address.getAdminArea();
        }
        if (name == null || name.trim().isEmpty()) {
            city.setText("도시 이름을 찾지 못했어요 · 연결을 확인해 주세요");
        } else {
            cityAnchor = anchor;
            city.setText(name);
        }
    }
}
