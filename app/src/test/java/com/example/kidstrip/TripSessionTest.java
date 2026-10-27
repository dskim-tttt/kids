package com.example.kidstrip;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripSessionTest {
    @Test public void confirmsCityOnlyAfterTwoObservationsOneMinuteApart() {
        TripSession s = new TripSession();
        assertFalse(s.observeCity("KR|경기|수원", "수원시", 0));
        assertTrue(s.needsCityConfirmation());
        assertFalse(s.observeCity("KR|경기|수원", "수원시", 59_999));
        assertTrue(s.observeCity("KR|경기|수원", "수원시", 60_000));
        assertEquals(1, s.stampCount());
        assertEquals("수원시", s.stamps().get(0).city);
        assertFalse(s.needsCityConfirmation());
    }
    @Test public void boundaryChangesDoNotCreatePrematureStamps() {
        TripSession s = new TripSession();
        s.observeCity("A", "수원", 0);
        assertFalse(s.observeCity("B", "용인", 60_000));
        assertFalse(s.observeCity("A", "수원", 120_000));
        assertEquals(0, s.stampCount());
        assertTrue(s.observeCity("A", "수원", 180_000));
    }
    @Test public void interruptionsAndFailedLookupsRestartConfirmation() {
        TripSession s = new TripSession();
        s.observeCity("A", "수원", 0);
        s.interruptCityConfirmation();
        assertFalse(s.observeCity("A", "수원", 60_000));
        s.observeCity(null, "", 70_000);
        assertFalse(s.observeCity("A", "수원", 120_000));
        assertTrue(s.observeCity("A", "수원", 180_000));
    }
    @Test public void longGapAndBackwardClockCannotConfirmAnOldCandidate() {
        TripSession s = new TripSession();
        s.observeCity("A", "수원", 0);
        assertFalse(s.observeCity("A", "수원", 400_000));
        assertFalse(s.observeCity("A", "수원", 10));
        assertEquals(0, s.stampCount());
    }
    @Test public void duplicateCitiesAreCollectedOnceAndOrderIsPreserved() {
        TripSession s = new TripSession();
        s.observeCity("A", "수원", 0);
        s.observeCity("A", "수원", 60_000);
        assertFalse(s.observeCity("A", "수원", 120_000));
        s.observeCity("B", "용인", 180_000);
        s.observeCity("B", "용인", 240_000);
        s.observeCity("A", "수원", 300_000);
        assertEquals(2, s.stampCount());
        assertEquals("수원", s.stamps().get(0).city);
        assertEquals("용인", s.stamps().get(1).city);
    }
    @Test public void sameNamedCitiesInDifferentRegionsRemainDistinct() {
        TripSession s = new TripSession();
        s.observeCity("US|A|Springfield", "Springfield", 0);
        s.observeCity("US|A|Springfield", "Springfield", 60_000);
        s.observeCity("US|B|Springfield", "Springfield", 120_000);
        s.observeCity("US|B|Springfield", "Springfield", 180_000);
        assertEquals(2, s.stampCount());
    }
    @Test public void findingACardTwiceDoesNotIncreaseDiscoveriesAndCardsWrap() {
        TripSession s = new TripSession();
        assertTrue(s.markFound());
        assertFalse(s.markFound());
        assertEquals(1, s.discoveryCount());
        for (int i = 0; i < TripSession.CARD_COUNT; i++) s.nextCard();
        assertEquals(0, s.cardIndex());
        assertTrue(s.currentCardFound());
    }
    @Test public void newJourneyClearsDiscoveriesStampsAndCandidates() {
        TripSession s = new TripSession();
        s.markFound(); s.nextCard();
        s.observeCity("A", "수원", 0);
        s.observeCity("A", "수원", 60_000);
        s.observeCity("B", "용인", 120_000);
        s.resetJourney();
        assertEquals(0, s.cardIndex());
        assertEquals(0, s.discoveryCount());
        assertEquals(0, s.stampCount());
        assertFalse(s.needsCityConfirmation());
        assertFalse(s.observeCity("B", "용인", 180_000));
    }
    @Test public void aFreshSessionHasNoPreviousTravelHistory() {
        TripSession old = new TripSession();
        old.markFound(); old.observeCity("A", "수원", 0); old.observeCity("A", "수원", 60_000);
        TripSession fresh = new TripSession();
        assertEquals(0, fresh.discoveryCount());
        assertTrue(fresh.stamps().isEmpty());
        old.stamps().clear();
        assertEquals(1, old.stampCount());
    }
    @Test public void newJourneyPreservesTheChosenSoundAndMotionSettings() {
        TripSession session = new TripSession();
        session.arrivalSoundEnabled = false;
        session.arrivalEffectsEnabled = false;
        session.resetJourney();
        assertFalse(session.arrivalSoundEnabled);
        assertFalse(session.arrivalEffectsEnabled);
        assertTrue(new TripSession().arrivalSoundEnabled);
    }
}
