package com.example.kidstrip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** City collection and play state. TripStore persists aggregates, never coordinates or routes. */
public final class TripSession {
    public static final long CITY_CONFIRM_MS = 60_000;
    private static final long MAX_OBSERVATION_GAP_MS = 360_000;
    public static final int CARD_COUNT = 6;
    public static final class Stamp {
        public final String key;
        public final String city;
        public final int visits;
        private Stamp(String key, String city, int visits) {
            this.key = key;
            this.city = city;
            this.visits = visits;
        }
    }
    private final LinkedHashMap<String, Stamp> stamps = new LinkedHashMap<>();
    private final Set<Integer> discoveries = new HashSet<>();
    private String candidate;
    private String confirmedCity;
    private long candidateSince, lastObservation;
    private int card;
    public int selectedTab;
    public boolean arrivalSoundEnabled = true;
    public boolean arrivalEffectsEnabled = true;

    public int cardIndex() { return card; }
    public boolean currentCardFound() { return discoveries.contains(card); }
    public int discoveryCount() { return discoveries.size(); }
    public boolean markFound() { return discoveries.add(card); }
    public void nextCard() { card = (card + 1) % CARD_COUNT; }
    public List<Stamp> stamps() { return new ArrayList<>(stamps.values()); }
    public int stampCount() { return stamps.size(); }
    public boolean needsCityConfirmation() { return candidate != null; }
    public void interruptCityConfirmation() { candidate = null; }

    /** A confirmed city change (including a return); the stamp collection stays unique.
     * Requires two observations >=60s apart, without interruption or a long gap.
     */
    public boolean observeCity(String key, String name, long elapsedMs) {
        if (key == null || key.trim().isEmpty() || name == null || name.trim().isEmpty() || elapsedMs < 0) {
            interruptCityConfirmation();
            return false;
        }
        if (key.equals(confirmedCity)) {
            interruptCityConfirmation();
            return false;
        }
        if (!key.equals(candidate) || elapsedMs < lastObservation
                || elapsedMs - lastObservation > MAX_OBSERVATION_GAP_MS) {
            candidate = key;
            candidateSince = elapsedMs;
            lastObservation = elapsedMs;
            return false;
        }
        lastObservation = elapsedMs;
        if (elapsedMs - candidateSince < CITY_CONFIRM_MS) return false;
        Stamp previous = stamps.get(key);
        int visits = previous == null ? 1 : previous.visits == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : previous.visits + 1;
        stamps.put(key, new Stamp(key, name.trim(), visits));
        confirmedCity = key;
        interruptCityConfirmation();
        return true;
    }
    // Persistence access stays package-private; confirmation timestamps and candidates are transient.
    String confirmedCityKey() { return confirmedCity; }
    Set<Integer> discoveredCardIndexes() { return new HashSet<>(discoveries); }
    void restoreStamp(String key, String city, int visits) {
        Stamp previous = stamps.get(key);
        if (previous == null || visits > previous.visits) {
            stamps.put(key, new Stamp(key, city, visits));
        }
    }
    void restoreConfirmedCity(String key) { confirmedCity = stamps.containsKey(key) ? key : null; }
    void restoreDiscoveries(Set<Integer> cards, int index) {
        for (int discovered : cards) {
            if (discovered >= 0 && discovered < CARD_COUNT) discoveries.add(discovered);
        }
        card = index >= 0 && index < CARD_COUNT ? index : 0;
    }

    /** Legacy model helper; the application no longer exposes collection deletion as a new trip. */
    public void resetJourney() {
        stamps.clear();
        confirmedCity = null;
        discoveries.clear();
        card = 0;
        interruptCityConfirmation();
    }
}
