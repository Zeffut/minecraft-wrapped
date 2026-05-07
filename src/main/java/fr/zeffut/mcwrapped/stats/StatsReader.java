package fr.zeffut.mcwrapped.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StatsReader {

    private StatsReader() {}

    public static Optional<Map<String, Map<String, Long>>> readSingleplayer(final MinecraftClient client) {
        if (!client.isInSingleplayer()) {
            return Optional.empty();
        }
        final MinecraftServer server = client.getServer();
        if (server == null) {
            return Optional.empty();
        }
        final UUID uuid = client.getSession().getUuidOrNull();
        if (uuid == null) {
            return Optional.empty();
        }
        final Path statsDir = server.getSavePath(WorldSavePath.STATS);
        final Path file = statsDir.resolve(uuid + ".json");
        if (!Files.exists(file)) {
            McWrappedClient.LOGGER.debug("No stats file at {}", file);
            return Optional.empty();
        }
        try {
            final String json = Files.readString(file);
            final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("stats")) {
                return Optional.of(new LinkedHashMap<>());
            }
            return Optional.of(parse(root.getAsJsonObject("stats")));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to read stats file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static Map<String, Map<String, Long>> parse(final JsonObject statsObj) {
        final Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> category : statsObj.entrySet()) {
            final JsonObject inner = category.getValue().getAsJsonObject();
            final Map<String, Long> values = new HashMap<>();
            for (final Map.Entry<String, JsonElement> stat : inner.entrySet()) {
                values.put(stat.getKey(), stat.getValue().getAsLong());
            }
            out.put(category.getKey(), values);
        }
        return out;
    }
}
