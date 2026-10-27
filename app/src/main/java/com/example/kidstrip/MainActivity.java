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
    private static final int CREAM = Color.rgb(246, 243, 233);
    private static final int TEAL = Color.rgb(36, 107, 100);
    private static final int INK = Color.rgb(23, 65, 62);
    private static final int YELLOW = Color.rgb(255, 206, 105);
    private TripSession session;
    private LinearLayout[] pages;
    private Button[] tabs;
    private Button foundButton;
    private ScrollView scroll;
    private SceneryView illustration;
    private TextView cardTitle, cardQuestion, cardFact, cardPosition, discoveries, stampSummary, tripStampSummary;
    private LinearLayout stampList;
    private LocationManager manager;
    private PhoneCompass phoneCompass;
    private CompassView compassDial;
    private TextView compassReading;
    private TextView speed, city, status;
    private Location latest, cityAnchor;
    private boolean active, listening, resolving;
    private long lastCityRequest;
    private int epoch, cityRequestId;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!active) return;
            updateSpeed();
            phoneCompass.expireStaleHeading();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Object retained = getLastNonConfigurationInstance();
        session = retained instanceof TripSession ? (TripSession) retained : new TripSession();
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CREAM);
        pages = new LinearLayout[]{page(R.id.page_trip), page(R.id.page_explore), page(R.id.page_stamps)};
        buildTripPage(pages[0]);
        buildExplorePage(pages[1]);
        buildStampsPage(pages[2]);
        phoneCompass = new PhoneCompass((android.hardware.SensorManager) getSystemService(SENSOR_SERVICE),
                () -> getWindowManager().getDefaultDisplay().getRotation(), new PhoneCompass.Callback() {
            @Override public void onHeading(float heading) {
                compassDial.setHeading(heading);
                String direction = getResources().getStringArray(R.array.compass_directions)[CompassMath.directionIndex(heading)];
                int degrees = CompassMath.roundedDegrees(heading);
                compassReading.setText(getString(R.string.compass_reading, direction, degrees));
                compassReading.setContentDescription(getString(R.string.compass_accessible, direction, degrees));
            }
            @Override public void onUnavailable(int message) {
                compassDial.setHeading(Float.NaN);
                compassReading.setText(message);
                compassReading.setContentDescription(getString(message));
            }
        });
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        for (LinearLayout page : pages) frame.addView(page);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(frame);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(dp(12), dp(6), dp(12), dp(8));
        int[] ids = {R.id.nav_trip, R.id.nav_explore, R.id.nav_stamps};
        int[] titles = {R.string.tab_trip, R.string.tab_explore, R.string.tab_stamps};
        tabs = new Button[3];
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i] = button(ids[i], titles[i], false);
            tabs[i].setOnClickListener(view -> selectTab(tab));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            navigation.addView(tabs[i], params);
        }
        root.addView(navigation);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(root);
        renderCard();
        renderStamps();
        selectTab(session.selectedTab);
    }

    @Override public Object onRetainNonConfigurationInstance() { return session; }

    private LinearLayout page(int id) {
        LinearLayout layout = new LinearLayout(this);
        layout.setId(id);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(20));
        layout.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -2));
        return layout;
    }
    private LinearLayout card(int background) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), dp(14));
        layout.setBackground(rounded(background));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, dp(6));
        layout.setLayoutParams(params);
        return layout;
    }
    private android.graphics.drawable.GradientDrawable rounded(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(24));
        return drawable;
    }
    private void buildTripPage(LinearLayout page) {
        page.addView(label(R.string.trip_title, 26));
        page.addView(label(R.string.trip_subtitle, 16));
        LinearLayout gauge = card(Color.WHITE);
        gauge.setPadding(dp(12), dp(8), dp(12), dp(14));
        LinearLayout instruments = new LinearLayout(this);
        instruments.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout speedColumn = new LinearLayout(this);
        speedColumn.setOrientation(LinearLayout.VERTICAL);
        speedColumn.setGravity(Gravity.CENTER);
        speedColumn.addView(label(R.string.speed_label, 16));
        speed = label("—", 84);
        speed.setId(R.id.gps_speed);
        speed.setMaxLines(1);
        speed.setAutoSizeTextTypeUniformWithConfiguration(36, 84, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        speed.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        speedColumn.addView(speed, new LinearLayout.LayoutParams(-1, dp(120)));
        speedColumn.addView(label(R.string.speed_unit, 22));
        instruments.addView(speedColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout compassColumn = new LinearLayout(this);
        compassColumn.setOrientation(LinearLayout.VERTICAL);
        compassColumn.setGravity(Gravity.CENTER);
        compassColumn.addView(label(R.string.compass_title, 15));
        compassDial = new CompassView(this);
        compassDial.setId(R.id.compass_dial);
        compassColumn.addView(compassDial, new LinearLayout.LayoutParams(dp(104), dp(104)));
        compassReading = label(R.string.compass_waiting, 16);
        compassReading.setId(R.id.compass_reading);
        compassColumn.addView(compassReading);
        instruments.addView(compassColumn, new LinearLayout.LayoutParams(dp(120), -2));
        gauge.addView(instruments);
        gauge.addView(label(R.string.compass_note, 13));
        page.addView(gauge);
        LinearLayout cityCard = card(Color.rgb(225, 236, 221));
        cityCard.addView(label(R.string.city_label, 16));
        city = label("위치를 기다리고 있어요", 30);
        city.setId(R.id.gps_city);
        cityCard.addView(city);
        page.addView(cityCard);
        status = label("GPS를 준비하고 있어요", 15);
        page.addView(status);
        LinearLayout invitation = card(YELLOW);
        invitation.addView(label(R.string.explore_title, 24));
        invitation.addView(label(R.string.explore_intro, 17));
        Button explore = button(View.NO_ID, R.string.explore_open, true);
        explore.setOnClickListener(view -> selectTab(1));
        invitation.addView(explore);
        page.addView(invitation);
        Button stamps = button(View.NO_ID, R.string.stamps_open, false);
        stamps.setOnClickListener(view -> selectTab(2));
        page.addView(stamps);
        tripStampSummary = label("", 16);
        page.addView(tripStampSummary);
        LinearLayout settings = card(Color.WHITE);
        settings.addView(label(R.string.settings_title, 18));
        Button permission = button(R.id.location_allow, R.string.location_allow, false);
        permission.setOnClickListener(v -> requestLocation());
        settings.addView(permission);
        Button locationSettings = button(View.NO_ID, R.string.location_settings, false);
        locationSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        settings.addView(locationSettings);
        settings.addView(label(R.string.location_privacy, 14));
        page.addView(settings);
    }
    private void buildExplorePage(LinearLayout page) {
        page.addView(label(R.string.explore_title, 28));
        page.addView(label(R.string.explore_intro, 17));
        LinearLayout mission = card(Color.WHITE);
        illustration = new SceneryView(this);
        mission.addView(illustration, new LinearLayout.LayoutParams(-1, dp(170)));
        cardPosition = label("", 14);
        mission.addView(cardPosition);
        cardTitle = label("", 26);
        cardTitle.setId(R.id.card_title);
        mission.addView(cardTitle);
        cardQuestion = label("", 20);
        mission.addView(cardQuestion);
        cardFact = label("", 17);
        mission.addView(cardFact);
        foundButton = button(R.id.card_found, R.string.found_button, true);
        foundButton.setOnClickListener(view -> {
            session.markFound();
            renderCard();
            discoveries.announceForAccessibility(discoveries.getText());
        });
        mission.addView(foundButton);
        Button next = button(R.id.card_next, R.string.next_card, false);
        next.setOnClickListener(view -> {
            session.nextCard();
            renderCard();
            cardTitle.announceForAccessibility(cardTitle.getText());
        });
        mission.addView(next);
        page.addView(mission);
        discoveries = label("", 17);
        discoveries.setId(R.id.discovery_summary);
        page.addView(discoveries);
        page.addView(label(R.string.explore_footer, 14));
    }
    private void buildStampsPage(LinearLayout page) {
        page.addView(label(R.string.stamps_title, 28));
        page.addView(label(R.string.stamps_intro, 17));
        stampSummary = label("", 20);
        page.addView(stampSummary);
        stampList = new LinearLayout(this);
        stampList.setId(R.id.stamp_list);
        stampList.setOrientation(LinearLayout.VERTICAL);
        page.addView(stampList);
        page.addView(label(R.string.stamps_explanation, 15));
        page.addView(label(R.string.session_privacy, 14));
        Button reset = button(R.id.new_journey, R.string.new_journey, false);
        reset.setOnClickListener(view -> new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.reset_title).setMessage(R.string.reset_message)
                .setNegativeButton(R.string.reset_cancel, null)
                .setPositiveButton(R.string.reset_confirm, (dialog, which) -> {
                    session.resetJourney();
                    // Discard in-flight pre-reset geocoding before allowing a new confirmation.
                    epoch++;
                    resolving = false;
                    cityRequestId++;
                    cityAnchor = null;
                    lastCityRequest = 0;
                    renderCard();
                    renderStamps();
                }).show());
        page.addView(reset);
    }
    private void selectTab(int index) {
        session.selectedTab = index;
        for (int i = 0; i < tabs.length; i++) {
            pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            tabs[i].setSelected(i == index);
            tabs[i].setBackground(rounded(i == index ? TEAL : Color.WHITE));
            tabs[i].setTextColor(i == index ? Color.WHITE : INK);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            pages[index].setAccessibilityPaneTitle(tabs[index].getText());
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }
    private void renderCard() {
        int index = session.cardIndex();
        int[][] content = {
            {R.string.card_river_title, R.string.card_river_question, R.string.card_river_fact},
            {R.string.card_mountain_title, R.string.card_mountain_question, R.string.card_mountain_fact},
            {R.string.card_bridge_title, R.string.card_bridge_question, R.string.card_bridge_fact},
            {R.string.card_tunnel_title, R.string.card_tunnel_question, R.string.card_tunnel_fact},
            {R.string.card_field_title, R.string.card_field_question, R.string.card_field_fact},
            {R.string.card_city_title, R.string.card_city_question, R.string.card_city_fact}
        };
        illustration.setScene(index);
        cardPosition.setText(getString(R.string.card_position, index + 1, TripSession.CARD_COUNT));
        cardTitle.setText(content[index][0]);
        cardQuestion.setText(content[index][1]);
        cardFact.setText(content[index][2]);
        foundButton.setEnabled(!session.currentCardFound());
        foundButton.setAlpha(session.currentCardFound() ? 0.65f : 1f);
        foundButton.setText(session.currentCardFound() ? R.string.found_already : R.string.found_button);
        discoveries.setText(getString(R.string.discovery_summary, session.discoveryCount()));
    }
    private void renderStamps() {
        String summary = getString(R.string.stamp_summary, session.stampCount());
        stampSummary.setText(summary);
        tripStampSummary.setText(summary);
        tabs[2].setContentDescription(getString(R.string.tab_stamps) + ", " + summary);
        stampList.removeAllViews();
        if (session.stampCount() == 0) {
            LinearLayout empty = card(Color.WHITE);
            empty.addView(label(R.string.stamps_empty, 22));
            stampList.addView(empty);
            return;
        }
        int number = 0;
        for (TripSession.Stamp stamp : session.stamps()) {
            LinearLayout tile = card(number % 2 == 0 ? YELLOW : Color.rgb(225, 236, 221));
            tile.addView(label(getString(R.string.stamp_number, ++number), 15));
            tile.addView(label(stamp.city, 30));
            tile.addView(label(R.string.stamp_confirmed, 16));
            stampList.addView(tile);
        }
    }
    private Button button(int id, int text, boolean primary) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setMinHeight(dp(52));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setBackgroundTintList(null);
        button.setBackground(rounded(primary ? TEAL : Color.rgb(234, 231, 219)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);
        return button;
    }
    private TextView label(int text, int size) { return label(getString(text), size); }
    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(INK);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onResume() {
        super.onResume();
        active = true;
        speed.setText("—");
        speed.setContentDescription(getString(R.string.speed_unknown));
        city.setText("위치를 기다리고 있어요");
        startGps();
        phoneCompass.start();
        handler.post(ticker);
    }
    @Override public void onPause() {
        active = false;
        phoneCompass.stop();
        session.interruptCityConfirmation();
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
        session.interruptCityConfirmation();
        speed.setText("—");
        speed.setContentDescription(getString(R.string.speed_unknown));
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
        speed.setContentDescription(value == null ? getString(R.string.speed_unknown)
                : getString(R.string.speed_reading, String.valueOf(value)));
        status.setText(value == null ? "GPS 속도를 확인하고 있어요 · 신호가 약할 수 있어요" : "GPS로 측정한 대략적인 속도예요");
        if (age < 0 || age > 10_000) {
            session.interruptCityConfirmation();
            city.setText("위치를 다시 확인하고 있어요");
            cityAnchor = null;
            lastCityRequest = 0;
        }
    }
    private void resolveCity(Location location) {
        long now = SystemClock.elapsedRealtime();
        if (resolving || (lastCityRequest > 0 && now - lastCityRequest < 60_000)) return;
        if (!session.needsCityConfirmation() && cityAnchor != null
                && cityAnchor.distanceTo(location) < 1000 && now - lastCityRequest < 300_000) return;
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
        long age = latest == null ? Long.MAX_VALUE
                : (SystemClock.elapsedRealtimeNanos() - latest.getElapsedRealtimeNanos());
        if (age < 0 || age > 10_000_000_000L) {
            session.interruptCityConfirmation();
            city.setText("위치를 다시 확인하고 있어요");
            return;
        }
        String name = null;
        String stampKey = null;
        if (addresses != null && !addresses.isEmpty()) {
            Address address = addresses.get(0);
            name = address.getLocality();
            if (name == null || name.trim().isEmpty()) name = address.getSubAdminArea();
            if (name != null && !name.trim().isEmpty()) {
                stampKey = String.valueOf(address.getCountryCode()) + "|"
                        + String.valueOf(address.getAdminArea()) + "|" + name.trim();
            }
            if (name == null || name.trim().isEmpty()) name = address.getAdminArea();
        }
        if (name == null || name.trim().isEmpty()) {
            session.interruptCityConfirmation();
            city.setText("도시 이름을 찾지 못했어요 · 연결을 확인해 주세요");
        } else {
            cityAnchor = anchor;
            city.setText(name);
            if (session.observeCity(stampKey, name, SystemClock.elapsedRealtime())) {
                renderStamps();
                if (session.selectedTab == 2) {
                    stampSummary.announceForAccessibility(getString(R.string.stamp_announcement, name));
                }
            }
        }
    }
}
