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

/**
 * Aggregates vanilla stats across every singleplayer world in the current game directory.
 *
 * <p>Server stats are not covered here — they live on the server and require packet capture
 * (planned for milestone S5).
 */
public final class StatsReader {

    private StatsReader() {}

    public static Optional<Map<String, Map<String, Long>>> readAggregated(final MinecraftClient client) {
        final UUID uuid = client.getSession().getUuidOrNull();
        if (uuid == null) {
            McWrappedClient.LOGGER.warn("No client session UUID, cannot read stats.");
            return Optional.empty();
        }
        final Path savesDir = FabricLoader.getInstance().getGameDir().resolve("saves");
        if (!Files.isDirectory(savesDir)) {
            McWrappedClient.LOGGER.info("No saves directory at {}.", savesDir);
            return Optional.of(new LinkedHashMap<>());
        }

        final Map<String, Map<String, Long>> aggregated = new LinkedHashMap<>();
        int worldsScanned = 0;
        int worldsWithStats = 0;

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
                    mergeInto(aggregated, root.getAsJsonObject("stats"));
                } catch (final IOException e) {
                    McWrappedClient.LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
                }
            }
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to scan saves dir: {}", e.getMessage());
            return Optional.empty();
        }

        McWrappedClient.LOGGER.info("Scanned {} world(s), {} had stats for player {}.",
                worldsScanned, worldsWithStats, uuid);
        return Optional.of(aggregated);
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
