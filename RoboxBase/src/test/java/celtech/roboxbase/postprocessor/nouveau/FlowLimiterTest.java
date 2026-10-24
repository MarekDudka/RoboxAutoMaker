package celtech.roboxbase.postprocessor.nouveau;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FlowLimiterTest
{

    @Test
    public void raisesSpeedUpToMaxFlow()
    {
        // 0.4 x 0.2 line = 0.08 mm3/mm, 10 mm long -> 0.8 mm3; max 4 mm3/s -> 50 mm/s
        assertEquals(50.0, FlowLimiter.calculateSpeed(30, 0.8, 10, 4, 80), 1e-9);
    }

    @Test
    public void respectsSpeedCap()
    {
        assertEquals(60.0, FlowLimiter.calculateSpeed(30, 0.8, 10, 10, 60), 1e-9);
    }

    @Test
    public void neverLowersSpeed()
    {
        // wide line 0.24 mm3/mm: max flow 4 -> 16.7 mm/s, current 30 stays
        assertEquals(30.0, FlowLimiter.calculateSpeed(30, 2.4, 10, 4, 80), 1e-9);
    }

    @Test
    public void leavesShortSegmentsAlone()
    {
        assertEquals(30.0, FlowLimiter.calculateSpeed(30, 0.08, 1.0, 4, 80), 1e-9);
    }

    @Test
    public void leavesNonExtrudingMovesAlone()
    {
        assertEquals(30.0, FlowLimiter.calculateSpeed(30, 0, 10, 4, 80), 1e-9);
    }
}
