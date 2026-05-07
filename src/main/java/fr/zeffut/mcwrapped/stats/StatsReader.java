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

    public static Optional<Aggregated> readAggregated(final MinecraftClient client, final ServerPlayTimeTracker serverTracker) {
        final UUID uuid = client.getSession().getUuidOrNull();
        if (uuid == null) {
            McWrappedClient.LOGGER.warn("No client session UUID, cannot read stats.");
            return Optional.empty();
        }
        final Map<String, Map<String, Long>> aggregated = new LinkedHashMap<>();
        final Map<String, Long> perWorldPlayTime = new LinkedHashMap<>();

        // Singleplayer worlds.
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

        // Servers (tracked client-side).
        long serverTotal = 0;
        for (final Map.Entry<String, Long> entry : serverTracker.playTimeByDisplayName().entrySet()) {
            perWorldPlayTime.merge(WorldKey.server(entry.getKey()), entry.getValue(), Long::sum);
            serverTotal += entry.getValue();
        }

        // Server play time also contributes to the aggregate "minecraft:custom"."minecraft:play_time".
        if (serverTotal > 0) {
            aggregated
                    .computeIfAbsent("minecraft:custom", k -> new LinkedHashMap<>())
                    .merge("minecraft:play_time", serverTotal, Long::sum);
        }

        McWrappedClient.LOGGER.info("Scanned {} world(s), {} had stats; {} server(s) tracked.",
                worldsScanned, worldsWithStats, serverTracker.playTimeByDisplayName().size());
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
