package com.example.kidstrip;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.view.View;
import android.widget.TextView;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSystemClock;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = CityStampIntegrationTest.FakeGeocoder.class)
public class CityStampIntegrationTest {
    @Implements(Geocoder.class)
    public static class FakeGeocoder {
        static List<Address> response;
        static boolean defer;
        static Geocoder.GeocodeListener pending;
        @Implementation protected static boolean isPresent() { return true; }
        @Implementation(minSdk = 33) protected void getFromLocation(double lat, double lon, int max, Geocoder.GeocodeListener listener) {
            if (defer) pending = listener;
            else listener.onGeocode(response);
        }
    }
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private TripSession session() { return activity.tripSession(); }
    @Before public void start() {
        FakeGeocoder.response = Collections.emptyList();
        FakeGeocoder.defer = false;
        FakeGeocoder.pending = null;
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
    }
    @After public void stop() { controller.pause().stop().destroy(); }
    private void city(String locality) {
        Address address = new Address(Locale.KOREAN);
        address.setCountryCode("KR");
        address.setAdminArea("경기도");
        address.setLocality(locality);
        FakeGeocoder.response = Collections.singletonList(address);
    }
    private void fix() {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(37.2636);
        location.setLongitude(127.0286);
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        location.setAccuracy(5);
        location.setSpeed(10);
        activity.onLocationChanged(location);
        shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void successfulCityLookupsCreateOneVisibleStampAfterConfirmation() {
        city("수원시");
        fix();
        assertEquals("수원시", ((TextView) activity.findViewById(R.id.gps_city)).getText().toString());
        assertEquals(0, session().stampCount());
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61));
        fix();
        assertEquals(1, session().stampCount());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        assertEquals("수원시", ((TextView) activity.findViewById(R.id.arrival_city)).getText().toString());
        activity.findViewById(R.id.arrival_close).performClick();
        activity.findViewById(R.id.nav_stamps).performClick();
        LinearLayout list = activity.findViewById(R.id.stamp_list);
        LinearLayout first = (LinearLayout) ((LinearLayout) list.getChildAt(0)).getChildAt(0);
        assertEquals("수원시", ((TextView) first.getChildAt(1)).getText().toString());
        assertEquals("36", ((TextView) activity.findViewById(R.id.gps_speed)).getText().toString());
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5));
        fix();
        assertEquals(1, session().stampCount());
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
    }
    @Test public void callbackFromBeforePauseCannotCreateAPendingCityOrStamp() {
        city("수원시");
        FakeGeocoder.defer = true;
        fix();
        Geocoder.GeocodeListener old = FakeGeocoder.pending;
        assertNotNull(old);
        controller.pause().resume();
        old.onGeocode(FakeGeocoder.response);
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(session().needsCityConfirmation());
        assertEquals(0, session().stampCount());
        assertNotEquals("수원시", ((TextView) activity.findViewById(R.id.gps_city)).getText().toString());
    }
    @Test public void provinceFallbackDoesNotInventACityStamp() {
        city(null);
        fix();
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5));
        fix();
        assertEquals("경기도", ((TextView) activity.findViewById(R.id.gps_city)).getText().toString());
        assertEquals(0, session().stampCount());
        assertFalse(session().needsCityConfirmation());
    }
    @Test public void anotherConfirmedCityGetsItsOwnBadgeAndBackgroundingClosesIt() {
        city("수원시"); fix();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        activity.findViewById(R.id.arrival_close).performClick();
        city("용인시");
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5)); fix();
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        assertEquals(2, session().stampCount());
        assertEquals("용인시", ((TextView) activity.findViewById(R.id.arrival_city)).getText().toString());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        controller.pause().resume();
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
    }
    @Test public void failedLookupDoesNotShowArrivalOrGiveAStamp() {
        city("수원시"); fix();
        FakeGeocoder.response = Collections.emptyList();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        assertEquals(0, session().stampCount());
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
    }
    @Test public void roundTripReplaysSoundAndReturnGreetingWithoutDuplicateStamps() {
        android.media.AudioManager audio = (android.media.AudioManager) activity.getSystemService(android.content.Context.AUDIO_SERVICE);
        android.app.NotificationManager notifications = (android.app.NotificationManager) activity.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        shadowOf(notifications).setNotificationPolicyAccessGranted(true);
        notifications.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
        audio.setRingerMode(android.media.AudioManager.RINGER_MODE_NORMAL);
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0);
        java.util.concurrent.atomic.AtomicInteger sounds = new java.util.concurrent.atomic.AtomicInteger();
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener((player, shadow) -> sounds.incrementAndGet());
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(source -> new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(750, 0));
        city("수원시"); fix();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        assertEquals(1, sounds.get());
        activity.findViewById(R.id.arrival_close).performClick();
        city("용인시");
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5)); fix();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        assertEquals(2, sounds.get());
        activity.findViewById(R.id.arrival_close).performClick();
        city("수원시");
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5)); fix();
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        assertEquals(2, session().stampCount());
        assertEquals(3, sounds.get());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
        LinearLayout panel = activity.findViewById(R.id.arrival_panel);
        assertEquals(activity.getString(R.string.arrival_revisit_title), ((TextView) panel.getChildAt(0)).getText().toString());
        assertEquals(activity.getString(R.string.arrival_revisit_subtitle), ((TextView) panel.getChildAt(3)).getText().toString());
        activity.findViewById(R.id.arrival_close).performClick();
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5)); fix();
        assertEquals(3, sounds.get());
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
    }
    @Test public void briefUnconfirmedNeighbourDoesNotReplayTheCurrentCityGreeting() {
        city("수원시"); fix();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        activity.findViewById(R.id.arrival_close).performClick();
        city("용인시");
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5)); fix();
        city("수원시");
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); fix();
        assertEquals(1, session().stampCount());
        assertEquals(View.GONE, activity.findViewById(R.id.city_arrival_overlay).getVisibility());
    }
}
