package fr.zeffut.mcwrapped.stats;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record MonthlyDelta(
        YearMonth month,
        Map<String, Map<String, Long>> deltas
) {

    public static MonthlyDelta compute(final StatsSnapshot before, final StatsSnapshot after) {
        final Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        final Set<String> categories = new HashSet<>();
        categories.addAll(before.statsRaw().keySet());
        categories.addAll(after.statsRaw().keySet());

        for (final String category : categories) {
            final Map<String, Long> beforeMap = before.statsRaw().getOrDefault(category, Map.of());
            final Map<String, Long> afterMap = after.statsRaw().getOrDefault(category, Map.of());
            final Map<String, Long> diff = new LinkedHashMap<>();
            final Set<String> keys = new HashSet<>();
            keys.addAll(beforeMap.keySet());
            keys.addAll(afterMap.keySet());
            for (final String key : keys) {
                final long d = afterMap.getOrDefault(key, 0L) - beforeMap.getOrDefault(key, 0L);
                if (d > 0) {
                    diff.put(key, d);
                }
            }
            if (!diff.isEmpty()) {
                result.put(category, diff);
            }
        }
        return new MonthlyDelta(after.month(), result);
    }

    public long total(final String category) {
        return deltas.getOrDefault(category, Map.of()).values().stream().mapToLong(Long::longValue).sum();
    }
}
