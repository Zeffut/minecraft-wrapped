package fr.zeffut.mcwrapped.telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsBucketerTest {

    private static final long TICKS_PER_HOUR = 20L * 60 * 60;

    @Test
    void playtimeBuckets() {
        assertEquals("<1h", StatsBucketer.playtimeBucket(0));
        assertEquals("<1h", StatsBucketer.playtimeBucket(TICKS_PER_HOUR - 1));
        assertEquals("1-10h", StatsBucketer.playtimeBucket(TICKS_PER_HOUR));
        assertEquals("1-10h", StatsBucketer.playtimeBucket(10 * TICKS_PER_HOUR - 1));
        assertEquals("10-50h", StatsBucketer.playtimeBucket(10 * TICKS_PER_HOUR));
        assertEquals("50-100h", StatsBucketer.playtimeBucket(50 * TICKS_PER_HOUR));
        assertEquals("100h+", StatsBucketer.playtimeBucket(100 * TICKS_PER_HOUR));
    }

    @Test
    void deathsBuckets() {
        assertEquals("0", StatsBucketer.deathsBucket(0));
        assertEquals("1-5", StatsBucketer.deathsBucket(1));
        assertEquals("1-5", StatsBucketer.deathsBucket(5));
        assertEquals("6-20", StatsBucketer.deathsBucket(6));
        assertEquals("21-50", StatsBucketer.deathsBucket(21));
        assertEquals("50+", StatsBucketer.deathsBucket(51));
    }

    @Test
    void distanceBuckets() {
        // input is centimeters
        assertEquals("<10km", StatsBucketer.distanceBucket(0));
        assertEquals("<10km", StatsBucketer.distanceBucket(10L * 1000 * 100 - 1));
        assertEquals("10-50km", StatsBucketer.distanceBucket(10L * 1000 * 100));
        assertEquals("50-100km", StatsBucketer.distanceBucket(50L * 1000 * 100));
        assertEquals("100-500km", StatsBucketer.distanceBucket(100L * 1000 * 100));
        assertEquals("500km+", StatsBucketer.distanceBucket(500L * 1000 * 100));
    }
}
