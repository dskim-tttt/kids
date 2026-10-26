package com.example.kidstrip;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeedPolicyTest {
    @Test public void convertsMetersPerSecondToKmh() {
        assertEquals(Integer.valueOf(36), SpeedPolicy.kmh(true,10,5,true,1,0));
    }
    @Test public void stationaryFixIsRealZero() {
        assertEquals(Integer.valueOf(0), SpeedPolicy.kmh(true,0,5,false,0,0));
    }
    @Test public void rejectsUnreliableOrMissingFixes() {
        assertNull(SpeedPolicy.kmh(false,0,5,false,0,0));
        assertNull(SpeedPolicy.kmh(true,10,80,false,0,0));
        assertNull(SpeedPolicy.kmh(true,10,5,true,4,0));
        assertNull(SpeedPolicy.kmh(true,10,5,false,0,10001));
        assertNull(SpeedPolicy.kmh(true,Float.NaN,5,false,0,0));
        assertNull(SpeedPolicy.kmh(true,-1,5,false,0,0));
    }
}
