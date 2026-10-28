package com.example.kidstrip;

import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CityLogoCatalogTest {
    private String official(String key, String asset) {
        return "{\"key\":\"" + key + "\",\"asset\":\"" + asset
                + "\",\"sourceKind\":\"official\",\"sourceUrl\":\"https://municipality.example/symbol\","
                + "\"license\":\"Reviewed permission\",\"verified\":true}";
    }
    private String manifest(String records) { return "{\"version\":1,\"entries\":[" + records + "]}"; }
    @Test public void emptyMissingOrMalformedRegistryNeverInventsALogo() {
        assertTrue(CityLogoCatalog.parse(manifest("")).isEmpty());
        assertTrue(CityLogoCatalog.parse("invalid").isEmpty());
        assertTrue(CityLogoCatalog.parse("{\"version\":2,\"entries\":[]}").isEmpty());
        CityBadgeView badge = new CityBadgeView(RuntimeEnvironment.getApplication());
        badge.setCity("XX|unknown|unregistered-city", "미등록 도시");
        assertFalse(badge.hasLogo());
        assertFalse(badge.isOfficialLogo());
        badge.setCity("수원시");
        assertFalse(badge.hasLogo());
    }
    @Test public void acceptsOnlyExactKeyAndCompleteOfficialProvenance() {
        String valid = official("KR|경기도|수원시", "city_logos/suwon.png");
        Map<String, CityLogoCatalog.Entry> entries = CityLogoCatalog.parse(manifest(valid));
        assertEquals(1, entries.size());
        assertEquals("official", entries.get("KR|경기도|수원시").sourceKind);
        assertNull(entries.get("수원시"));
        assertTrue(CityLogoCatalog.parse(manifest(valid.replace("\"verified\":true", "\"verified\":false"))).isEmpty());
        assertTrue(CityLogoCatalog.parse(manifest(valid.replace("Reviewed permission", ""))).isEmpty());
        assertTrue(CityLogoCatalog.parse(manifest(valid.replace("https://", "http://"))).isEmpty());
    }
    @Test public void unsafeAssetPathsAreRejected() {
        assertTrue(CityLogoCatalog.isSafeAssetPath("city_logos/suwon.png"));
        assertTrue(CityLogoCatalog.isSafeAssetPath("city_logos/city-01.webp"));
        for (String path : new String[]{"../suwon.png", "city_logos/../suwon.png", "/city_logos/suwon.png",
                "https://host/suwon.png", "city_logos/folder/suwon.png", "city_logos/suwon.svg",
                "city_logos/suwon.PNG", "city_logos/%2e%2e.png", "city_logos/a..png"}) {
            assertFalse(path, CityLogoCatalog.isSafeAssetPath(path));
        }
    }
    @Test public void conflictingKeysAreNotArbitrarilyResolved() {
        String a = official("KR|경기도|수원시", "city_logos/suwon.png");
        String b = official("KR|경기도|수원시", "city_logos/other.png");
        assertTrue(CityLogoCatalog.parse(manifest(a + "," + b + "," + a)).isEmpty());
    }
    @Test public void originalArtworkIsExplicitlySeparateFromOfficialLogos() {
        String original = "{\"key\":\"KR|경기도|수원시\",\"asset\":\"city_logos/travel.png\","
                + "\"sourceKind\":\"original\",\"license\":\"Original app artwork\"}";
        CityLogoCatalog.Entry entry = CityLogoCatalog.parse(manifest(original)).get("KR|경기도|수원시");
        assertNotNull(entry);
        assertEquals("original", entry.sourceKind);
        assertEquals("", entry.sourceUrl);
        String sprite = original.substring(0, original.length() - 1) + ",\"cell\":[1,2,4,3]}";
        CityLogoCatalog.Entry spriteEntry = CityLogoCatalog.parse(manifest(sprite)).get("KR|경기도|수원시");
        assertEquals(1, spriteEntry.cell.column);
        assertEquals(2, spriteEntry.cell.row);
        assertEquals("original", spriteEntry.sourceKind);
        assertTrue(CityLogoCatalog.parse(manifest(original.replace("original", "unreviewed"))).isEmpty());
    }
    @Test public void spriteCellsShareAnAssetButUseIndependentExactCityKeys() {
        String first = official("KR|서울특별시|서울특별시", "city_logos/sprite.png");
        first = first.substring(0, first.length() - 1) + ",\"cell\":[0,0,4,3]}";
        String alias = first.replace("KR|서울특별시|서울특별시", "KR|서울특별시|서울시");
        Map<String, CityLogoCatalog.Entry> entries = CityLogoCatalog.parse(manifest(first + "," + alias));
        assertEquals(2, entries.size());
        CityLogoCatalog.Entry entry = entries.get("KR|서울특별시|서울특별시");
        assertEquals(4, entry.cell.columns);
        assertEquals(3, entry.cell.rows);
        assertEquals(new android.graphics.Rect(0, 0, 384, 341), CityLogoCatalog.sourceFor(entry, 1536, 1024));
        String last = first.replace("[0,0,4,3]", "[3,2,4,3]");
        entry = CityLogoCatalog.parse(manifest(last)).get("KR|서울특별시|서울특별시");
        assertEquals(new android.graphics.Rect(1152, 682, 1536, 1024), CityLogoCatalog.sourceFor(entry, 1536, 1024));
    }
    @Test public void invalidSpriteGridsAreRejectedRatherThanShowingTheWrongCity() {
        String base = official("A", "city_logos/sprite.png");
        base = base.substring(0, base.length() - 1);
        for (String cell : new String[]{"null", "[]", "[0,0,4]", "[-1,0,4,3]", "[4,0,4,3]", "[0,3,4,3]",
                "[0,0,0,3]", "[0,0,4,0]", "[0.5,0,4,3]", "[0,0,9999,3]", "[\"0\",0,4,3]"}) {
            assertTrue(cell, CityLogoCatalog.parse(manifest(base + ",\"cell\":" + cell + "}")).isEmpty());
        }
        CityLogoCatalog.Entry full = CityLogoCatalog.parse(manifest(official("A", "city_logos/logo.png"))).get("A");
        assertEquals(new android.graphics.Rect(0, 0, 400, 200), CityLogoCatalog.sourceFor(full, 400, 200));
        assertNull(CityLogoCatalog.sourceFor(full, 0, 100));
    }
}
