package com.example.kidstrip;
import android.location.Address;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CityIdentityTest {
    private Address address(String region, String city) {
        Address a = new Address(Locale.KOREAN); a.setCountryCode("KR"); a.setAdminArea(region); a.setLocality(city); return a;
    }
    @Test public void metropolitanAreaWithoutLocalityIsARealCity() {
        CityIdentity c = CityIdentity.from(address("서울특별시", null));
        assertNotNull(c); assertEquals("KR|서울특별시|서울특별시", c.key);
    }
    @Test public void provinceWithoutCityIsNotACity() { assertNull(CityIdentity.from(address("경기도", null))); }
    @Test public void cityAliasesAndRenamedProvinceHaveStableKeys() {
        assertEquals(CityIdentity.from(address("경기도", "수원시")).key, CityIdentity.from(address("경기도", "수원")).key);
        assertEquals(CityIdentity.from(address("강원도", "춘천시")).key, CityIdentity.from(address("강원특별자치도", "춘천")).key);
    }
    @Test public void sameCityNameInDifferentRegionsStaysDistinct() {
        Address a = address("A", "Springfield"); a.setCountryCode("US");
        Address b = address("B", "Springfield"); b.setCountryCode("US");
        assertNotEquals(CityIdentity.from(a).key, CityIdentity.from(b).key);
    }
    @Test public void bundledOriginalBadgesResolveByCanonicalCityKey() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        String[][] cities = {{"서울특별시", null}, {"경기도", "수원"}, {"경기도", "용인"}, {"경기도", "성남"},
                {"인천광역시", null}, {"강원도", "춘천"}, {"강원도", "강릉"}, {"강원도", "원주"},
                {"대전광역시", null}, {"세종특별자치시", null}, {"충청북도", "청주"}, {"충청남도", "천안"}};
        for (String[] parts : cities) {
            CityIdentity identity = CityIdentity.from(address(parts[0], parts[1]));
            CityBadgeView badge = new CityBadgeView(context);
            badge.setCity(identity.key, identity.name);
            assertTrue(identity.key, badge.hasLogo());
            assertTrue(badge.isOriginalBadge());
            assertFalse(badge.isOfficialLogo());
        }
    }
}
