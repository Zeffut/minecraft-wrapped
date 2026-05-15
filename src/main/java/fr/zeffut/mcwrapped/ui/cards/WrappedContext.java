package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.stats.MonthlyDelta;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Bundles the stats backing a Wrapped sequence and exposes helpers for the cards.
 */
public record WrappedContext(YearMonth month, MonthlyDelta delta) {

    private Map<String, Long> custom() {
        return delta.deltas().getOrDefault("minecraft:custom", Map.of());
    }

    public long playTimeTicks() { return custom().getOrDefault("minecraft:play_time", 0L); }
    public long deaths()        { return custom().getOrDefault("minecraft:deaths", 0L); }
    public long mobKills()      { return custom().getOrDefault("minecraft:mob_kills", 0L); }
    public long walkCm()        { return custom().getOrDefault("minecraft:walk_one_cm", 0L); }
    public long sprintCm()      { return custom().getOrDefault("minecraft:sprint_one_cm", 0L); }
    public long jumps()         { return custom().getOrDefault("minecraft:jump", 0L); }

    public List<Map.Entry<String, Long>> topMined(final int n) { return top("minecraft:mined", n); }
    public List<Map.Entry<String, Long>> topKilled(final int n) { return top("minecraft:killed", n); }
    public List<Map.Entry<String, Long>> topKilledBy(final int n) { return top("minecraft:killed_by", n); }

    public List<Map.Entry<String, Long>> topWorlds(final int n) {
        return filteredWorlds().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .toList();
    }

    public int worldsCount() { return filteredWorlds().size(); }

    /**
     * Apply user-configured include / exclude lists. Empty include list means "all"; the exclude
     * list always takes precedence and removes matching keys.
     */
    private Map<String, Long> filteredWorlds() {
        final fr.zeffut.mcwrapped.config.McWrappedConfig cfg = fr.zeffut.mcwrapped.config.ConfigManager.get();
        final Map<String, Long> raw = delta.perWorldPlayTimeDelta();
        if (cfg.includedWorlds.isEmpty() && cfg.excludedWorlds.isEmpty()) return raw;
        final java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        for (final var e : raw.entrySet()) {
            final String k = e.getKey();
            if (!cfg.includedWorlds.isEmpty() && !cfg.includedWorlds.contains(k)) continue;
            if (cfg.excludedWorlds.contains(k)) continue;
            out.put(k, e.getValue());
        }
        return out;
    }

    public int playersMet()       { return delta.playersMetDelta(); }
    public long messagesSent()    { return delta.messagesSentDelta(); }
    public long commandsSent()    { return delta.commandsSentDelta(); }
    public int serversVisited()   { return delta.serversVisitedDelta(); }
    public long soloTicks()       { return delta.soloTicks(); }
    public long serverTicks()     { return delta.serverTicks(); }

    private List<Map.Entry<String, Long>> top(final String category, final int n) {
        return delta.deltas().getOrDefault(category, Map.of()).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .toList();
    }
}
