package com.example.kidstrip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Memory-only play state. Never contains coordinates or writes to disk/Bundles. */
public final class TripSession {
    public static final long CITY_CONFIRM_MS = 60_000;
    private static final long MAX_OBSERVATION_GAP_MS = 360_000;
    public static final int CARD_COUNT = 6;
    public static final class Stamp {
        public final String city;
        private Stamp(String city) { this.city = city; }
    }
    private final LinkedHashMap<String, Stamp> stamps = new LinkedHashMap<>();
    private final Set<Integer> discoveries = new HashSet<>();
    private String candidate;
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

    /** Two successful observations >=60s apart, without interruption or a long gap. */
    public boolean observeCity(String key, String name, long elapsedMs) {
        if (key == null || key.trim().isEmpty() || name == null || name.trim().isEmpty() || elapsedMs < 0) {
            interruptCityConfirmation();
            return false;
        }
        if (stamps.containsKey(key)) {
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
        stamps.put(key, new Stamp(name.trim()));
        interruptCityConfirmation();
        return true;
    }
    public void resetJourney() {
        stamps.clear();
        discoveries.clear();
        card = 0;
        interruptCityConfirmation();
    }
}
