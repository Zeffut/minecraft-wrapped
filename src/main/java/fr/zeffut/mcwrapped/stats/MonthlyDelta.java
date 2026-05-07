package fr.zeffut.mcwrapped.stats;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record MonthlyDelta(
        YearMonth month,
        Map<String, Map<String, Long>> deltas,
        Map<String, Long> perWorldPlayTimeDelta,
        int playersMetDelta,
        long messagesSentDelta,
        long commandsSentDelta,
        int serversVisitedDelta
) {

    public MonthlyDelta(final YearMonth month, final Map<String, Map<String, Long>> deltas) {
        this(month, deltas, Map.of(), 0, 0L, 0L, 0);
    }

    public MonthlyDelta(final YearMonth month, final Map<String, Map<String, Long>> deltas, final Map<String, Long> perWorldPlayTimeDelta) {
        this(month, deltas, perWorldPlayTimeDelta, 0, 0L, 0L, 0);
    }

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
                if (d > 0) diff.put(key, d);
            }
            if (!diff.isEmpty()) result.put(category, diff);
        }

        final Map<String, Long> perWorldDelta = new LinkedHashMap<>();
        final Set<String> worlds = new HashSet<>();
        worlds.addAll(before.perWorldPlayTime().keySet());
        worlds.addAll(after.perWorldPlayTime().keySet());
        for (final String w : worlds) {
            final long d = after.perWorldPlayTime().getOrDefault(w, 0L) - before.perWorldPlayTime().getOrDefault(w, 0L);
            if (d > 0) perWorldDelta.put(w, d);
        }

        final int playersDelta = Math.max(0, after.playersSeenCount() - before.playersSeenCount());
        final long messagesDelta = Math.max(0, after.messagesSent() - before.messagesSent());
        final long commandsDelta = Math.max(0, after.commandsSent() - before.commandsSent());
        final int serversDelta = Math.max(0, after.serversVisitedCount() - before.serversVisitedCount());

        return new MonthlyDelta(after.month(), result, perWorldDelta,
                playersDelta, messagesDelta, commandsDelta, serversDelta);
    }

    public long total(final String category) {
        return deltas.getOrDefault(category, Map.of()).values().stream().mapToLong(Long::longValue).sum();
    }

    public long totalPlayTimeTicks() {
        return perWorldPlayTimeDelta.values().stream().mapToLong(Long::longValue).sum();
    }

    public long soloTicks() {
        return perWorldPlayTimeDelta.entrySet().stream()
                .filter(e -> WorldKey.isSingleplayer(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    public long serverTicks() {
        return perWorldPlayTimeDelta.entrySet().stream()
                .filter(e -> WorldKey.isServer(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }
}
