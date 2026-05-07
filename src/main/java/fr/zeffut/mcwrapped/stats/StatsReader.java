package fr.zeffut.mcwrapped.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class StatsReader {

    public record Aggregated(
            Map<String, Map<String, Long>> total,
            Map<String, Long> perWorldPlayTime
    ) {}

    private StatsReader() {}

    public static Optional<Aggregated> readAggregated(final MinecraftClient client,
                                                      final ServerPlayTimeTracker serverPlayTime,
                                                      final ServerStatsTracker serverStats) {
        final UUID uuid = client.getSession().getUuidOrNull();
        if (uuid == null) {
            McWrappedClient.LOGGER.warn("No client session UUID, cannot read stats.");
            return Optional.empty();
        }
        final Map<String, Map<String, Long>> aggregated = new LinkedHashMap<>();
        final Map<String, Long> perWorldPlayTime = new LinkedHashMap<>();

        // 1. Singleplayer worlds.
        final Path savesDir = FabricLoader.getInstance().getGameDir().resolve("saves");
        int worldsScanned = 0;
        int worldsWithStats = 0;
        if (Files.isDirectory(savesDir)) {
            try (final Stream<Path> stream = Files.list(savesDir)) {
                for (final Path world : (Iterable<Path>) stream::iterator) {
                    if (!Files.isDirectory(world)) continue;
                    worldsScanned++;
                    final Path file = world.resolve("stats").resolve(uuid + ".json");
                    if (!Files.exists(file)) continue;
                    worldsWithStats++;
                    try {
                        final String json = Files.readString(file);
                        final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                        if (!root.has("stats")) continue;
                        final JsonObject statsObj = root.getAsJsonObject("stats");
                        mergeInto(aggregated, statsObj);
                        final long playTime = readPlayTime(statsObj);
                        if (playTime > 0) {
                            perWorldPlayTime.put(WorldKey.singleplayer(world.getFileName().toString()), playTime);
                        }
                    } catch (final IOException e) {
                        McWrappedClient.LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
                    }
                }
            } catch (final IOException e) {
                McWrappedClient.LOGGER.warn("Failed to scan saves dir: {}", e.getMessage());
            }
        }

        // 2. Servers — full vanilla stats from REQUEST_STATS responses.
        for (final Map.Entry<String, Map<String, Long>> cat : serverStats.aggregatedStats().entrySet()) {
            final Map<String, Long> bucket = aggregated.computeIfAbsent(cat.getKey(), k -> new LinkedHashMap<>());
            for (final Map.Entry<String, Long> stat : cat.getValue().entrySet()) {
                bucket.merge(stat.getKey(), stat.getValue(), Long::sum);
            }
        }

        // 3. Per-server play_time. Prefer the server-side stat (more accurate); fall back to
        //    the client-side tick counter for servers that don't respond to REQUEST_STATS.
        final Map<String, Long> fromStats = serverStats.serverPlayTimes();
        for (final Map.Entry<String, Long> entry : fromStats.entrySet()) {
            perWorldPlayTime.merge(WorldKey.server(entry.getKey()), entry.getValue(), Long::sum);
        }
        for (final Map.Entry<String, Long> entry : serverPlayTime.playTimeByDisplayName().entrySet()) {
            if (fromStats.containsKey(entry.getKey())) continue;
            perWorldPlayTime.merge(WorldKey.server(entry.getKey()), entry.getValue(), Long::sum);
        }

        // 4. If only the tick counter has play time for some server (no stats response), still
        //    contribute to aggregate play_time so the time-spent card is consistent.
        long tickCounterFallback = 0;
        for (final Map.Entry<String, Long> entry : serverPlayTime.playTimeByDisplayName().entrySet()) {
            if (fromStats.containsKey(entry.getKey())) continue;
            tickCounterFallback += entry.getValue();
        }
        if (tickCounterFallback > 0) {
            aggregated
                    .computeIfAbsent("minecraft:custom", k -> new LinkedHashMap<>())
                    .merge("minecraft:play_time", tickCounterFallback, Long::sum);
        }

        McWrappedClient.LOGGER.info("Stats sources — SP: {}/{} worlds, server stats: {}, server tickers: {}.",
                worldsWithStats, worldsScanned, fromStats.size(), serverPlayTime.playTimeByDisplayName().size());
        return Optional.of(new Aggregated(aggregated, perWorldPlayTime));
    }

    private static long readPlayTime(final JsonObject statsObj) {
        if (!statsObj.has("minecraft:custom")) return 0L;
        final JsonObject custom = statsObj.getAsJsonObject("minecraft:custom");
        if (!custom.has("minecraft:play_time")) return 0L;
        return custom.get("minecraft:play_time").getAsLong();
    }

    private static void mergeInto(final Map<String, Map<String, Long>> target, final JsonObject statsObj) {
        for (final Map.Entry<String, JsonElement> category : statsObj.entrySet()) {
            final Map<String, Long> bucket = target.computeIfAbsent(category.getKey(), k -> new LinkedHashMap<>());
            for (final Map.Entry<String, JsonElement> stat : category.getValue().getAsJsonObject().entrySet()) {
                bucket.merge(stat.getKey(), stat.getValue().getAsLong(), Long::sum);
            }
        }
    }
}
