package com.example.kidstrip;

import android.location.Address;
import java.util.Locale;

/** Stable city keys; Korean metropolitan areas count even when Geocoder omits locality. */
public final class CityIdentity {
    public final String key, name;
    private CityIdentity(String key, String name) { this.key = key; this.name = name; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String metropolitan(String value) {
        String[] names = {"서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시", "세종특별자치시"};
        for (String name : names) if (value.equals(name) || value.equals(name.substring(0, 2)) || value.equals(name.substring(0, 2) + "시")) return name;
        return null;
    }
    public static CityIdentity from(Address address) {
        String country = clean(address.getCountryCode()).toUpperCase(Locale.ROOT);
        String region = clean(address.getAdminArea());
        String name = clean(address.getLocality());
        if (name.isEmpty()) name = clean(address.getSubAdminArea());
        if (country.equals("KR")) {
            String metro = metropolitan(region);
            if (metro != null) { region = metro; name = metro; }
            else {
                String[] cities = {"수원", "용인", "성남", "춘천", "강릉", "원주", "청주", "천안"};
                for (String city : cities) if (name.equals(city)) { name = city + "시"; break; }
                if (region.equals("강원도")) region = "강원특별자치도";
            }
        }
        if (name.isEmpty()) return null; // A province alone must never be invented into a city.
        return new CityIdentity((country.isEmpty() ? "unknown" : country) + "|" + region + "|" + name, name);
    }
}
