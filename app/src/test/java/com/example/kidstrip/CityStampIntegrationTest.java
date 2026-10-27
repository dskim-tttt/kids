package com.example.kidstrip;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
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
    private TripSession session() { return (TripSession) activity.onRetainNonConfigurationInstance(); }
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
        activity.findViewById(R.id.nav_stamps).performClick();
        LinearLayout list = activity.findViewById(R.id.stamp_list);
        LinearLayout first = (LinearLayout) list.getChildAt(0);
        assertEquals("수원시", ((TextView) first.getChildAt(1)).getText().toString());
        assertEquals("36", ((TextView) activity.findViewById(R.id.gps_speed)).getText().toString());
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5));
        fix();
        assertEquals(1, session().stampCount());
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
}
