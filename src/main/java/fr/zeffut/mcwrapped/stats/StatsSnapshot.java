package fr.zeffut.mcwrapped.stats;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;

public record StatsSnapshot(
        YearMonth month,
        Instant capturedAt,
        Map<String, Map<String, Long>> statsRaw,
        Map<String, Long> perWorldPlayTime,
        int playersSeenCount,
        long messagesSent,
        long commandsSent,
        int serversVisitedCount
) {
}
