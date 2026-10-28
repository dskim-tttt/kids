package com.example.kidstrip;

import android.content.Context;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SoundCatalogTest {
    @Test public void threeSamplesHaveDistinctPlayableWavResourcesAndReadableLabels() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Set<Integer> resources = new HashSet<>();
        assertEquals(3, SoundCatalog.IDS.length);
        assertEquals(SoundCatalog.IDS.length, SoundCatalog.TITLE_RES.length);
        for (int i = 0; i < SoundCatalog.IDS.length; i++) {
            int resource = SoundCatalog.rawFor(SoundCatalog.IDS[i]);
            assertTrue(resources.add(resource));
            assertFalse(context.getString(SoundCatalog.TITLE_RES[i]).trim().isEmpty());
            try (InputStream in = context.getResources().openRawResource(resource)) {
                byte[] header = new byte[12];
                assertEquals(12, in.read(header));
                assertEquals("RIFF", new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
                assertEquals("WAVE", new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
                assertTrue(in.available() > 20_000);
            }
        }
    }
    @Test public void savedMissingOrOldIdsUseTheDefaultInsteadOfCrashing() {
        assertEquals(SoundCatalog.DEFAULT_ID, SoundCatalog.normalize(null));
        assertEquals(SoundCatalog.DEFAULT_ID, SoundCatalog.normalize("old-name"));
        assertEquals(1, SoundCatalog.indexOf("bell"));
        assertEquals("sparkle", SoundCatalog.normalize("sparkle"));
    }
}
