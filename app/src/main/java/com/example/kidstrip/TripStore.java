package com.example.kidstrip;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Versioned app-private LOCAL collection store. No coordinates, route points or network calls. */
public final class TripStore {
    private static final Object STORE_LOCK = new Object();
    private static final int VERSION = 1;
    private static final String PREFERENCES = "trip_collection";
    private static final String SNAPSHOT = "snapshot";
    private final SharedPreferences preferences;
    private boolean writable = true;
    private boolean recoveryWarning;

    public TripStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** Returns all recoverable entries while leaving an unrecognized/damaged original untouched. */
    public TripSession load() {
        synchronized (STORE_LOCK) { return readSnapshot(); }
    }

    private TripSession readSnapshot() {
        TripSession session = new TripSession();
        writable = true;
        recoveryWarning = false;
        String raw;
        try {
            raw = preferences.getString(SNAPSHOT, null);
        } catch (ClassCastException invalidType) {
            protectOriginal();
            return session;
        }
        if (raw == null) return session;
        try {
            JSONObject document = new JSONObject(raw);
            if (document.optInt("version", -1) != VERSION) {
                protectOriginal();
                return session;
            }
            JSONArray cities = array(document, "cities");
            if (cities != null) {
                for (int index = 0; index < cities.length(); index++) {
                    JSONObject city = cities.optJSONObject(index);
                    if (city == null) { protectOriginal(); continue; }
                    Object key = city.opt("key"), name = city.opt("city"), count = city.opt("visits");
                    if (!(key instanceof String) || ((String) key).trim().isEmpty()
                            || !(name instanceof String) || ((String) name).trim().isEmpty()
                            || !positiveInteger(count)) {
                        protectOriginal();
                        continue;
                    }
                    session.restoreStamp((String) key, ((String) name).trim(), ((Number) count).intValue());
                }
            }
            Set<Integer> discovered = new HashSet<>();
            JSONArray cards = array(document, "discoveries");
            if (cards != null) {
                for (int index = 0; index < cards.length(); index++) {
                    Object card = cards.opt(index);
                    if (!(card instanceof Number) || ((Number) card).doubleValue() != ((Number) card).intValue()
                            || ((Number) card).intValue() < 0 || ((Number) card).intValue() >= TripSession.CARD_COUNT) {
                        protectOriginal();
                        continue;
                    }
                    discovered.add(((Number) card).intValue());
                }
            }
            int cursor = document.optInt("cardIndex", 0);
            if (cursor < 0 || cursor >= TripSession.CARD_COUNT) { protectOriginal(); cursor = 0; }
            session.restoreDiscoveries(discovered, cursor);
            Object confirmed = document.opt("confirmedCity");
            if (confirmed instanceof String) session.restoreConfirmedCity((String) confirmed);
        } catch (JSONException invalidJson) {
            protectOriginal();
        }
        return session;
    }

    private JSONArray array(JSONObject document, String name) {
        if (!document.has(name)) return null;
        JSONArray result = document.optJSONArray(name);
        if (result == null) protectOriginal();
        return result;
    }

    private static boolean positiveInteger(Object value) {
        if (!(value instanceof Number)) return false;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number >= 1 && number <= Integer.MAX_VALUE
                && number == ((Number) value).intValue();
    }

    private void protectOriginal() {
        writable = false;
        recoveryWarning = true;
    }

    public boolean hasRecoveryWarning() { return recoveryWarning; }

    /** False means the UI should report a save problem; existing local data is never erased. */
    public boolean save(TripSession session) {
        synchronized (STORE_LOCK) { return saveSnapshot(session); }
    }

    private boolean saveSnapshot(TripSession session) {
        // Collections are cumulative: a stale Activity/model must not erase a newer snapshot.
        TripSession cumulative = readSnapshot();
        if (!writable) return false;
        Map<String, Integer> storedCounts = new HashMap<>();
        for (TripSession.Stamp stamp : cumulative.stamps()) storedCounts.put(stamp.key, stamp.visits);
        boolean hasNewArrival = false;
        for (TripSession.Stamp stamp : session.stamps()) {
            if (stamp.visits > storedCounts.getOrDefault(stamp.key, 0)) hasNewArrival = true;
            cumulative.restoreStamp(stamp.key, stamp.city, stamp.visits);
        }
        cumulative.restoreDiscoveries(session.discoveredCardIndexes(), session.cardIndex());
        if (session.confirmedCityKey() != null && (hasNewArrival || cumulative.confirmedCityKey() == null)) {
            cumulative.restoreConfirmedCity(session.confirmedCityKey());
        }
        try {
            JSONObject document = new JSONObject();
            document.put("version", VERSION);
            JSONArray cities = new JSONArray();
            for (TripSession.Stamp stamp : cumulative.stamps()) {
                JSONObject entry = new JSONObject();
                entry.put("key", stamp.key);
                entry.put("city", stamp.city);
                entry.put("visits", stamp.visits);
                cities.put(entry);
            }
            document.put("cities", cities);
            JSONArray discoveries = new JSONArray();
            // Stable ordering makes local snapshots deterministic and easy to migrate.
            Set<Integer> cards = cumulative.discoveredCardIndexes();
            for (int card = 0; card < TripSession.CARD_COUNT; card++) if (cards.contains(card)) discoveries.put(card);
            document.put("discoveries", discoveries);
            document.put("cardIndex", cumulative.cardIndex());
            document.put("confirmedCity", cumulative.confirmedCityKey() == null ? JSONObject.NULL : cumulative.confirmedCityKey());
            return preferences.edit().putString(SNAPSHOT, document.toString()).commit();
        } catch (JSONException | RuntimeException error) {
            return false;
        }
    }
}
