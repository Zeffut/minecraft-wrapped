package fr.zeffut.mcwrapped.stats;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;

public record StatsSnapshot(
        YearMonth month,
        String contextId,
        Instant capturedAt,
        Map<String, Map<String, Long>> statsRaw
) {
}
