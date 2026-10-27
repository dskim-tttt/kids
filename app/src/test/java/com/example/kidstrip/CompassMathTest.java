package com.example.kidstrip;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompassMathTest {
    @Test public void normalizesNegativeAndMultipleTurns() {
        assertEquals(350f, CompassMath.normalize(-10), 0.001f);
        assertEquals(0f, CompassMath.normalize(720), 0.001f);
        assertTrue(Float.isNaN(CompassMath.normalize(Float.NaN)));
    }
    @Test public void eightDirectionsHaveCorrectBoundaries() {
        assertEquals(0, CompassMath.directionIndex(0));
        assertEquals(0, CompassMath.directionIndex(22.49f));
        assertEquals(1, CompassMath.directionIndex(22.5f));
        assertEquals(2, CompassMath.directionIndex(90));
        assertEquals(4, CompassMath.directionIndex(180));
        assertEquals(6, CompassMath.directionIndex(270));
        assertEquals(0, CompassMath.directionIndex(337.5f));
    }
    @Test public void smoothingCrossesNorthByTheShortArc() {
        assertEquals(359.7f, CompassMath.smooth(359, 1), 0.01f);
        assertEquals(0.3f, CompassMath.smooth(1, 359), 0.01f);
        assertEquals(90f, CompassMath.smooth(Float.NaN, 90), 0.001f);
    }
    @Test public void roundingCannotDisplay360Degrees() {
        assertEquals(0, CompassMath.roundedDegrees(359.9f));
        assertEquals(90, CompassMath.roundedDegrees(89.9f));
    }
    @Test public void derivesScreenTopBearingAndRejectsVerticalOrCorruptVectors() {
        assertEquals(0f, CompassMath.fromScreenMatrix(new float[]{1,0,0,0,1,0,0,0,1}), 0.001f);
        assertEquals(90f, CompassMath.fromScreenMatrix(new float[]{0,1,0,-1,0,0,0,0,1}), 0.001f);
        assertTrue(Float.isNaN(CompassMath.fromScreenMatrix(new float[]{1,0,0,0,0,1,0,1,0})));
        assertTrue(Float.isNaN(CompassMath.fromScreenMatrix(new float[]{1,Float.NaN,0,0,1,0,0,0,1})));
    }
    @Test public void unknownHeadingIsNeverLabelledAsNorth() {
        assertThrows(IllegalArgumentException.class, () -> CompassMath.directionIndex(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> CompassMath.roundedDegrees(Float.NaN));
    }
}
