package fr.zeffut.mcwrapped.telemetry;

/**
 * Maps raw stat values to coarse string buckets so the telemetry never carries
 * re-identifying exact numbers. Pure functions, no dependencies.
 */
public final class StatsBucketer {

    private static final long TICKS_PER_HOUR = 20L * 60 * 60;
    private static final long CM_PER_KM = 1000L * 100;

    private StatsBucketer() {}

    public static String playtimeBucket(final long playTimeTicks) {
        final long hours = playTimeTicks / TICKS_PER_HOUR;
        if (hours < 1) return "<1h";
        if (hours < 10) return "1-10h";
        if (hours < 50) return "10-50h";
        if (hours < 100) return "50-100h";
        return "100h+";
    }

    public static String deathsBucket(final long deaths) {
        if (deaths <= 0) return "0";
        if (deaths <= 5) return "1-5";
        if (deaths <= 20) return "6-20";
        if (deaths <= 50) return "21-50";
        return "50+";
    }

    public static String distanceBucket(final long distanceCm) {
        final long km = distanceCm / CM_PER_KM;
        if (km < 10) return "<10km";
        if (km < 50) return "10-50km";
        if (km < 100) return "50-100km";
        if (km < 500) return "100-500km";
        return "500km+";
    }
}
