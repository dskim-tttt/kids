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
    private TripStore tripStore;
    private TravelTracker travel;
    private ArrivalSound sampleSound;
    private boolean useGpsDirection = true;
    private String soundId;
    private int lastAudioFailure;
    private boolean soundAttemptActive, lastPlaybackFromOverlay;
    private TextView directionTitle, directionNote, odoText, tripText, launchText, storageStatus, audioStatus;
    private static final class RetainedState {
        final TripSession session;
        final TravelTracker travel;
        RetainedState(TripSession session, TravelTracker travel) { this.session = session; this.travel = travel; }
    }
    private CityArrivalOverlay cityArrivalOverlay;
    private android.content.SharedPreferences arrivalPreferences;
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
            if (!useGpsDirection) phoneCompass.expireStaleHeading();
            updateDirection();
            updateDistances();
            updateAudioStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Object retained = getLastNonConfigurationInstance();
        tripStore = new TripStore(this);
        TripSession storedSession = tripStore.load();
        session = retained instanceof RetainedState ? ((RetainedState) retained).session : storedSession;
        travel = retained instanceof RetainedState ? ((RetainedState) retained).travel : new TravelTracker(this);
        sampleSound = new ArrivalSound(this);
        arrivalPreferences = getSharedPreferences("arrival_preferences", MODE_PRIVATE);
        session.arrivalSoundEnabled = arrivalPreferences.getBoolean("sound_enabled", true);
        session.arrivalEffectsEnabled = arrivalPreferences.getBoolean("effects_enabled", true);
        useGpsDirection = arrivalPreferences.getBoolean("gps_direction", true);
        soundId = SoundCatalog.normalize(arrivalPreferences.getString("sound_id", SoundCatalog.DEFAULT_ID));
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
                if (useGpsDirection) return;
                compassDial.setHeading(heading);
                String direction = getResources().getStringArray(R.array.compass_directions)[CompassMath.directionIndex(heading)];
                int degrees = CompassMath.roundedDegrees(heading);
                compassReading.setText(getString(R.string.compass_reading, direction, degrees));
                compassReading.setContentDescription(getString(R.string.compass_accessible, direction, degrees));
            }
            @Override public void onUnavailable(int message) {
                if (useGpsDirection) return;
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
        android.widget.FrameLayout stage = new android.widget.FrameLayout(this);
        stage.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        cityArrivalOverlay = new CityArrivalOverlay(this, stage, root);
        stage.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(stage);
        renderCard();
        renderStamps();
        selectTab(session.selectedTab);
    }

    @Override public Object onRetainNonConfigurationInstance() { return new RetainedState(session, travel); }
    TripSession tripSession() { return session; }
    TravelTracker travelTracker() { return travel; }

    private void saveCollection() {
        if (!tripStore.save(session)) storageStatus.setText(R.string.storage_problem);
        else storageStatus.setText(R.string.storage_local);
    }
    private void updateDistances() {
        odoText.setText(getString(R.string.distance_km, travel.getOdoMeters() / 1000));
        tripText.setText(getString(R.string.distance_km, travel.getTripMeters() / 1000));
        launchText.setText(getString(R.string.distance_launch, travel.getSessionMeters() / 1000));
    }
    private void updateDirection() {
        directionTitle.setText(useGpsDirection ? R.string.gps_direction_title : R.string.compass_title);
        directionNote.setText(useGpsDirection ? R.string.gps_direction_note : R.string.compass_note);
        if (!useGpsDirection) return;
        Float heading = travel.getCourseDegrees();
        compassDial.setHeading(heading == null ? Float.NaN : heading);
        if (heading == null) {
            compassReading.setText(R.string.gps_direction_waiting);
            compassReading.setContentDescription(getString(R.string.gps_direction_waiting));
        } else {
            String direction = getResources().getStringArray(R.array.compass_directions)[CompassMath.directionIndex(heading)];
            int degrees = CompassMath.roundedDegrees(heading);
            compassReading.setText(getString(R.string.compass_reading, direction, degrees));
            compassReading.setContentDescription(getString(R.string.gps_direction_accessible, direction, degrees));
        }
    }
    private void updateAudioStatus() {
        int liveReason = sampleSound.availabilityReason();
        if (soundAttemptActive) {
            int playbackReason = lastPlaybackFromOverlay ? cityArrivalOverlay.lastSoundReason() : sampleSound.lastReason();
            lastAudioFailure = playbackReason == R.string.audio_reason_playback ? playbackReason : 0;
        }
        int reason = liveReason != 0 ? liveReason : lastAudioFailure != 0 ? lastAudioFailure
                : !session.arrivalSoundEnabled ? R.string.audio_reason_disabled : R.string.audio_waits_for_city;
        audioStatus.setText(reason);
    }

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
        directionTitle = label(R.string.gps_direction_title, 15);
        compassColumn.addView(directionTitle);
        compassDial = new CompassView(this);
        compassDial.setId(R.id.compass_dial);
        compassColumn.addView(compassDial, new LinearLayout.LayoutParams(dp(104), dp(104)));
        compassReading = label(R.string.compass_waiting, 16);
        compassReading.setId(R.id.compass_reading);
        compassColumn.addView(compassReading);
        instruments.addView(compassColumn, new LinearLayout.LayoutParams(dp(120), -2));
        gauge.addView(instruments);
        directionNote = label(R.string.gps_direction_note, 13);
        gauge.addView(directionNote);
        page.addView(gauge);
        LinearLayout cityCard = card(Color.rgb(225, 236, 221));
        cityCard.addView(label(R.string.city_label, 16));
        city = label("위치를 기다리고 있어요", 30);
        city.setId(R.id.gps_city);
        cityCard.addView(city);
        page.addView(cityCard);
        LinearLayout distances = card(Color.WHITE);
        distances.setPadding(dp(12), dp(8), dp(12), dp(10));
        LinearLayout meters = new LinearLayout(this);
        LinearLayout odoColumn = new LinearLayout(this);
        odoColumn.setOrientation(LinearLayout.VERTICAL);
        TextView odoLabel = label(R.string.distance_odo, 16);
        odoLabel.setPadding(0, dp(2), 0, dp(2)); odoColumn.addView(odoLabel);
        odoText = label("", 24); odoText.setId(R.id.distance_odo);
        odoText.setPadding(0, dp(2), 0, dp(2)); odoColumn.addView(odoText);
        meters.addView(odoColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout tripColumn = new LinearLayout(this);
        tripColumn.setOrientation(LinearLayout.VERTICAL);
        TextView tripLabel = label(R.string.distance_trip, 16);
        tripLabel.setPadding(0, dp(2), 0, dp(2)); tripColumn.addView(tripLabel);
        tripText = label("", 24); tripText.setId(R.id.distance_trip);
        tripText.setPadding(0, dp(2), 0, dp(2)); tripColumn.addView(tripText);
        meters.addView(tripColumn, new LinearLayout.LayoutParams(0, -2, 1));
        distances.addView(meters);
        launchText = label("", 15); launchText.setId(R.id.distance_launch);
        launchText.setPadding(0, dp(4), 0, dp(4));
        Button resetTrip = button(R.id.reset_trip, R.string.distance_reset_trip, false);
        resetTrip.setOnClickListener(view -> new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.distance_reset_title).setMessage(R.string.distance_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.distance_reset_trip, (dialog, which) -> {
                    travel.resetTrip(); updateDistances();
                }).show());
        LinearLayout tripActions = new LinearLayout(this);
        tripActions.setGravity(Gravity.CENTER_VERTICAL);
        tripActions.addView(launchText, new LinearLayout.LayoutParams(0, -2, 1));
        tripActions.addView(resetTrip, new LinearLayout.LayoutParams(-2, -2));
        distances.addView(tripActions);
        TextView distanceNote = label(R.string.distance_note, 13);
        distanceNote.setPadding(0, dp(2), 0, dp(2)); distances.addView(distanceNote);
        page.addView(distances);
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
        android.widget.Switch direction = new android.widget.Switch(this);
        direction.setId(R.id.gps_direction_toggle);
        direction.setText(R.string.gps_direction_setting);
        direction.setTextSize(17); direction.setMinHeight(dp(52));
        direction.setChecked(useGpsDirection);
        direction.setOnCheckedChangeListener((button, checked) -> {
            useGpsDirection = checked;
            arrivalPreferences.edit().putBoolean("gps_direction", checked).apply();
            if (phoneCompass != null) {
                phoneCompass.stop();
                if (!checked && active) phoneCompass.start();
            }
            updateDirection();
        });
        settings.addView(direction, new LinearLayout.LayoutParams(-1, -2));
        android.widget.Switch sound = new android.widget.Switch(this);
        sound.setId(R.id.arrival_sound_toggle);
        sound.setText(R.string.arrival_sound_setting);
        sound.setTextSize(17);
        sound.setMinHeight(dp(52));
        sound.setChecked(session.arrivalSoundEnabled);
        sound.setOnCheckedChangeListener((button, checked) -> {
            session.arrivalSoundEnabled = checked;
            arrivalPreferences.edit().putBoolean("sound_enabled", checked).apply();
            updateAudioStatus();
        });
        settings.addView(sound, new LinearLayout.LayoutParams(-1, -2));
        android.widget.Switch effects = new android.widget.Switch(this);
        effects.setId(R.id.arrival_effects_toggle);
        effects.setText(R.string.arrival_effects_setting);
        effects.setTextSize(17);
        effects.setMinHeight(dp(52));
        effects.setChecked(session.arrivalEffectsEnabled);
        effects.setOnCheckedChangeListener((button, checked) -> {
            session.arrivalEffectsEnabled = checked;
            arrivalPreferences.edit().putBoolean("effects_enabled", checked).apply();
        });
        settings.addView(effects, new LinearLayout.LayoutParams(-1, -2));
        settings.addView(label(R.string.arrival_settings_note, 14));
        settings.addView(label(R.string.audio_sample_picker, 17));
        android.widget.Spinner samples = new android.widget.Spinner(this);
        samples.setId(R.id.sound_samples);
        String[] names = new String[SoundCatalog.TITLE_RES.length];
        for (int i = 0; i < names.length; i++) names[i] = getString(SoundCatalog.TITLE_RES[i]);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        samples.setAdapter(adapter);
        samples.setSelection(SoundCatalog.indexOf(soundId));
        samples.setMinimumHeight(dp(52));
        samples.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                soundId = SoundCatalog.IDS[position];
                lastAudioFailure = 0;
                soundAttemptActive = false;
                arrivalPreferences.edit().putString("sound_id", soundId).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        settings.addView(samples, new LinearLayout.LayoutParams(-1, -2));
        Button hear = button(R.id.sound_sample_preview, R.string.audio_sample_preview, false);
        hear.setOnClickListener(view -> {
            soundAttemptActive = true;
            lastPlaybackFromOverlay = false;
            sampleSound.play(soundId);
            updateAudioStatus();
            if (sampleSound.lastReason() == 0) audioStatus.setText(R.string.audio_ready);
        });
        settings.addView(hear);
        audioStatus = label("", 15); audioStatus.setId(R.id.audio_status);
        settings.addView(audioStatus);
        storageStatus = label(tripStore.hasRecoveryWarning() ? R.string.storage_problem : R.string.storage_local, 14);
        storageStatus.setId(R.id.storage_status);
        settings.addView(storageStatus);
        Button preview = button(R.id.arrival_preview, R.string.arrival_preview_button, false);
        preview.setOnClickListener(view -> {
            soundAttemptActive = session.arrivalSoundEnabled;
            lastPlaybackFromOverlay = true;
            cityArrivalOverlay.show(getString(R.string.arrival_preview_city), CityArrivalOverlay.Kind.PREVIEW,
                    session.arrivalSoundEnabled, session.arrivalEffectsEnabled, soundId, "KR|경기도|수원시");
            updateAudioStatus();
        });
        settings.addView(preview);
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
            saveCollection();
            renderCard();
            discoveries.announceForAccessibility(discoveries.getText());
        });
        mission.addView(foundButton);
        Button next = button(R.id.card_next, R.string.next_card, false);
        next.setOnClickListener(view -> {
            session.nextCard();
            saveCollection();
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
        LinearLayout row = null;
        int index = 0;
        for (TripSession.Stamp stamp : session.stamps()) {
            if (index % 4 == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                stampList.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(2), dp(8), dp(2), dp(10));
            tile.setTag(stamp.key);
            CityBadgeView badge = new CityBadgeView(this);
            badge.setCity(stamp.key, stamp.city);
            tile.addView(badge, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView name = label(stamp.city, 14); name.setMaxLines(2);
            name.setPadding(0, dp(4), 0, dp(2));
            tile.addView(name);
            TextView visits = label(getString(R.string.stamp_visits, stamp.visits), 14);
            visits.setPadding(0, 0, 0, dp(2));
            tile.addView(visits);
            TextView logoType = label(badge.isOfficialLogo() ? R.string.logo_official
                    : badge.isOriginalBadge() ? R.string.logo_original : R.string.logo_pending, 10);
            logoType.setPadding(0, 0, 0, 0);
            tile.addView(logoType);
            tile.setContentDescription(getString(R.string.stamp_accessible, stamp.city, stamp.visits));
            tile.setOnClickListener(view -> new android.app.AlertDialog.Builder(this).setTitle(stamp.city)
                    .setMessage(getString(R.string.stamp_visits, stamp.visits)).setPositiveButton(android.R.string.ok, null).show());
            row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
            index++;
        }
        while (index % 4 != 0) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            index++;
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
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        active = true;
        speed.setText("—");
        speed.setContentDescription(getString(R.string.speed_unknown));
        city.setText("위치를 기다리고 있어요");
        startGps();
        if (!useGpsDirection) phoneCompass.start();
        updateDirection();
        updateDistances();
        updateAudioStatus();
        handler.post(ticker);
    }
    @Override public void onPause() {
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        active = false;
        cityArrivalOverlay.hide();
        sampleSound.stop();
        saveCollection();
        travel.clearAnchor();
        travel.save();
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
        travel.accept(location);
        updateDirection();
        updateDistances();
        updateSpeed();
        long age = (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000;
        if (age >= 0 && age <= 10_000 && location.hasAccuracy() && location.getAccuracy() <= 100) {
            resolveCity(location);
        }
    }
    @Override public void onProviderDisabled(String provider) {
        latest = null;
        travel.clearAnchor();
        updateDirection();
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
            travel.clearAnchor();
            session.interruptCityConfirmation();
            city.setText("위치를 다시 확인하고 있어요");
            cityAnchor = null;
            lastCityRequest = 0;
        }
    }
    private void resolveCity(Location location) {
        long now = SystemClock.elapsedRealtime();
        if (resolving || (lastCityRequest > 0 && now - lastCityRequest < 60_000)) return;
        // One lookup per minute: do not hide nearby city changes behind a five-minute cache.
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
            CityIdentity identity = CityIdentity.from(address);
            name = identity == null ? address.getAdminArea() : identity.name;
            stampKey = identity == null ? null : identity.key;
        }
        if (name == null || name.trim().isEmpty()) {
            session.interruptCityConfirmation();
            city.setText("도시 이름을 찾지 못했어요 · 연결을 확인해 주세요");
        } else {
            cityAnchor = anchor;
            city.setText(name);
            int previousStampCount = session.stampCount();
            if (session.observeCity(stampKey, name, SystemClock.elapsedRealtime())) {
                saveCollection();
                renderStamps();
                CityArrivalOverlay.Kind kind = session.stampCount() > previousStampCount
                        ? CityArrivalOverlay.Kind.FIRST_VISIT : CityArrivalOverlay.Kind.RETURN_VISIT;
                cityArrivalOverlay.show(name, kind, session.arrivalSoundEnabled, session.arrivalEffectsEnabled, soundId, stampKey);
                if (session.arrivalSoundEnabled) {
                    soundAttemptActive = true;
                    lastPlaybackFromOverlay = true;
                    updateAudioStatus();
                }
            }
        }
    }
}
