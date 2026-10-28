package com.example.kidstrip;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TripStoreTest {
    private Context context;
    private SharedPreferences preferences;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("trip_collection", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }
    private void city(TripSession session, String key, String name, long first) {
        assertFalse(session.observeCity(key, name, first));
        assertTrue(session.observeCity(key, name, first + TripSession.CITY_CONFIRM_MS));
    }
    @Test public void collectionCountsDiscoveriesAndCardResumeFromLocalStorage() {
        TripStore store = new TripStore(context);
        TripSession session = store.load();
        city(session, "KR|경기|수원", "수원시", 0);
        city(session, "KR|경기|용인", "용인시", 120_000);
        city(session, "KR|경기|수원", "수원시", 240_000);
        session.markFound(); session.nextCard(); session.markFound();
        assertTrue(store.save(session));
        TripSession reopened = new TripStore(context).load();
        assertEquals(2, reopened.stampCount());
        assertEquals("KR|경기|수원", reopened.stamps().get(0).key);
        assertEquals("수원시", reopened.stamps().get(0).city);
        assertEquals(2, reopened.stamps().get(0).visits);
        assertEquals(1, reopened.stamps().get(1).visits);
        assertEquals(2, reopened.discoveryCount());
        assertEquals(1, reopened.cardIndex());
        assertTrue(reopened.currentCardFound());
        assertFalse(reopened.needsCityConfirmation());
    }
    @Test public void reopeningInCurrentCityDoesNotAwardAnotherVisit() {
        TripStore store = new TripStore(context);
        TripSession session = store.load();
        city(session, "A", "수원시", 0);
        assertTrue(store.save(session));
        TripSession reopened = new TripStore(context).load();
        assertFalse(reopened.observeCity("A", "수원시", 0));
        assertFalse(reopened.observeCity("A", "수원시", 60_000));
        assertEquals(1, reopened.stamps().get(0).visits);
        city(reopened, "B", "용인시", 120_000);
        city(reopened, "A", "수원시", 240_000);
        assertEquals(2, reopened.stamps().get(0).visits);
    }
    @Test public void unfinishedConfirmationAndElapsedClockAreNeverPersisted() {
        TripStore store = new TripStore(context);
        TripSession session = store.load();
        assertFalse(session.observeCity("A", "수원시", 0));
        assertTrue(store.save(session));
        TripSession reopened = new TripStore(context).load();
        assertFalse(reopened.needsCityConfirmation());
        assertFalse(reopened.observeCity("A", "수원시", 60_000));
        assertTrue(reopened.observeCity("A", "수원시", 120_000));
        String snapshot = preferences.getString("snapshot", "");
        assertFalse(snapshot.contains("candidate"));
        assertFalse(snapshot.contains("elapsed"));
    }
    @Test public void malformedRecordDoesNotDiscardOtherCitiesOrOverwriteTheOriginal() {
        String raw = "{\"version\":1,\"cities\":[{\"key\":\"A\",\"city\":\"수원시\",\"visits\":3},{\"key\":\"B\",\"city\":7,\"visits\":1},{\"key\":\"C\",\"city\":\"성남시\",\"visits\":2}],\"discoveries\":[0,2],\"cardIndex\":2,\"confirmedCity\":\"A\"}";
        preferences.edit().putString("snapshot", raw).commit();
        TripStore store = new TripStore(context);
        TripSession recovered = store.load();
        assertEquals(2, recovered.stampCount());
        assertEquals(3, recovered.stamps().get(0).visits);
        assertEquals("성남시", recovered.stamps().get(1).city);
        assertEquals(2, recovered.discoveryCount());
        assertTrue(store.hasRecoveryWarning());
        assertFalse(store.save(recovered));
        assertEquals(raw, preferences.getString("snapshot", ""));
    }
    @Test public void damagedJsonAndUnknownFutureSchemaArePreserved() {
        for (String raw : new String[]{"broken data", "{\"version\":900,\"cities\":[{\"key\":\"A\",\"city\":\"수원\",\"visits\":1}]}"}) {
            preferences.edit().putString("snapshot", raw).commit();
            TripStore store = new TripStore(context);
            assertEquals(0, store.load().stampCount());
            assertTrue(store.hasRecoveryWarning());
            assertFalse(store.save(new TripSession()));
            assertEquals(raw, preferences.getString("snapshot", ""));
        }
    }
    @Test public void duplicateEntriesKeepTheLargestKnownCountWithoutDoubleCounting() {
        String raw = "{\"version\":1,\"cities\":[{\"key\":\"A\",\"city\":\"수원시\",\"visits\":5},{\"key\":\"A\",\"city\":\"수원시\",\"visits\":3}]}";
        preferences.edit().putString("snapshot", raw).commit();
        TripStore store = new TripStore(context);
        TripSession session = store.load();
        assertEquals(1, session.stampCount());
        assertEquals(5, session.stamps().get(0).visits);
        assertTrue(store.save(session));
        assertEquals(5, new TripStore(context).load().stamps().get(0).visits);
    }
    @Test public void invalidCounterAndDiscoveriesCannotEraseValidEntries() {
        String raw = "{\"version\":1,\"cities\":[{\"key\":\"A\",\"city\":\"수원시\",\"visits\":-1},{\"key\":\"B\",\"city\":\"용인시\",\"visits\":2}],\"discoveries\":[1,99,\"invalid\"]}";
        preferences.edit().putString("snapshot", raw).commit();
        TripStore store = new TripStore(context);
        TripSession session = store.load();
        assertEquals(1, session.stampCount());
        assertEquals("용인시", session.stamps().get(0).city);
        assertEquals(1, session.discoveryCount());
        assertFalse(store.save(session));
        assertEquals(raw, preferences.getString("snapshot", ""));
    }
    @Test public void savedDocumentStoresOnlyAggregateCollectionFields() throws Exception {
        TripStore store = new TripStore(context);
        TripSession session = store.load();
        city(session, "A", "수원시", 0);
        assertTrue(store.save(session));
        JSONObject document = new JSONObject(preferences.getString("snapshot", ""));
        assertEquals(1, document.getInt("version"));
        assertEquals("A", document.getString("confirmedCity"));
        JSONObject stamp = document.getJSONArray("cities").getJSONObject(0);
        assertEquals(3, stamp.length());
        assertTrue(stamp.has("key") && stamp.has("city") && stamp.has("visits"));
        assertFalse(document.has("latitude") || document.has("longitude") || document.has("route"));
    }
    @Test public void staleSnapshotsCannotEraseCollectedCitiesOrHigherVisitCounts() {
        TripStore store = new TripStore(context);
        TripSession first = store.load();
        city(first, "A", "수원시", 0);
        first.markFound();
        assertTrue(store.save(first));
        TripSession stale = new TripStore(context).load();
        city(first, "B", "용인시", 120_000);
        city(first, "A", "수원시", 240_000);
        assertTrue(store.save(first));
        assertTrue(new TripStore(context).save(stale));
        TripSession recovered = new TripStore(context).load();
        assertEquals(2, recovered.stampCount());
        assertEquals(2, recovered.stamps().get(0).visits);
        assertEquals(1, recovered.discoveryCount());
        // A fresh transient model cannot accidentally act as collection deletion either.
        assertTrue(new TripStore(context).save(new TripSession()));
        assertEquals(2, new TripStore(context).load().stampCount());
    }
    @Test public void saveWithoutPriorLoadStillProtectsAnUnrecognizedSnapshot() {
        String raw = "{\"version\":900,\"cities\":[]}";
        preferences.edit().putString("snapshot", raw).commit();
        TripStore store = new TripStore(context);
        assertFalse(store.save(new TripSession()));
        assertTrue(store.hasRecoveryWarning());
        assertEquals(raw, preferences.getString("snapshot", ""));
    }
    @Test public void staleSaveCannotRewindTheLastConfirmedCity() {
        TripStore store = new TripStore(context);
        TripSession current = store.load();
        city(current, "A", "수원시", 0);
        assertTrue(store.save(current));
        TripSession stale = new TripStore(context).load();
        city(current, "B", "용인시", 120_000);
        assertTrue(store.save(current));
        assertTrue(new TripStore(context).save(stale));
        TripSession reopened = new TripStore(context).load();
        assertFalse(reopened.observeCity("B", "용인시", 0));
        assertEquals(1, reopened.stamps().get(1).visits);
    }
}
