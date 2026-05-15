package fr.zeffut.mcwrapped.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures full vanilla stats from servers via the {@code REQUEST_STATS} packet,
 * persists them per-server, and exposes the merged map to {@link StatsReader}.
 *
 * <p>Best-effort: some server-side anti-cheats may suppress stats responses. When that
 * happens we just keep whatever the client received last; nothing breaks.
 */
public final class ServerStatsTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int REQUEST_INTERVAL_TICKS = 6000; // 5 minutes
    private static final int CAPTURE_INTERVAL_TICKS = 1200; // 1 minute

    private final Path file;
    private final Map<String, ServerEntry> entries = new LinkedHashMap<>();
    private int ticksSinceRequest = 0;
    private int ticksSinceCapture = 0;

    public record ServerEntry(String displayName, Map<String, Map<String, Long>> stats) {}

    public ServerStatsTracker() {
        this.file = FabricLoader.getInstance().getGameDir().resolve("wrapped").resolve("server-stats.json");
        load();
    }

    public void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ticksSinceRequest = 0;
            ticksSinceCapture = 0;
            requestStats(client);
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            captureFromHandler(client);
            save();
        });
    }

    /** Per-category aggregated map across all known servers. */
    public Map<String, Map<String, Long>> aggregatedStats() {
        final Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (final ServerEntry e : entries.values()) {
            for (final Map.Entry<String, Map<String, Long>> cat : e.stats.entrySet()) {
                final Map<String, Long> bucket = out.computeIfAbsent(cat.getKey(), k -> new LinkedHashMap<>());
                for (final Map.Entry<String, Long> stat : cat.getValue().entrySet()) {
                    bucket.merge(stat.getKey(), stat.getValue(), Long::sum);
                }
            }
        }
        return out;
    }

    /** Per-server play_time pulled from server-side stats (more accurate than client tick counter). */
    public Map<String, Long> serverPlayTimes() {
        final Map<String, Long> out = new LinkedHashMap<>();
        for (final ServerEntry e : entries.values()) {
            final Map<String, Long> custom = e.stats.getOrDefault("minecraft:custom", Map.of());
            final long playTime = custom.getOrDefault("minecraft:play_time", 0L);
            if (playTime > 0) {
                out.merge(e.displayName, playTime, Long::sum);
            }
        }
        return out;
    }

    private void onTick(final Minecraft client) {
        if (client.world == null || client.isInSingleplayer() || client.getCurrentServerEntry() == null) return;
        ticksSinceRequest++;
        ticksSinceCapture++;
        if (ticksSinceCapture >= CAPTURE_INTERVAL_TICKS) {
            ticksSinceCapture = 0;
            captureFromHandler(client);
        }
        if (ticksSinceRequest >= REQUEST_INTERVAL_TICKS) {
            ticksSinceRequest = 0;
            requestStats(client);
        }
    }

    private void requestStats(final Minecraft client) {
        if (client.player == null || client.player.networkHandler == null) return;
        try {
            client.player.networkHandler.sendPacket(
                    new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Mode.REQUEST_STATS));
        } catch (final RuntimeException e) {
            McWrappedClient.LOGGER.debug("REQUEST_STATS send failed: {}", e.getMessage());
        }
    }

    private void captureFromHandler(final Minecraft client) {
        if (client.player == null) return;
        final ServerData info = client.getCurrentServerEntry();
        if (info == null || info.address == null) return;

        final StatsCounter handler = client.player.getStatHandler();
        final Map<String, Map<String, Long>> snapshot = new LinkedHashMap<>();

        for (final StatType<?> type : BuiltInRegistries.STAT_TYPE) {
            final ResourceLocation categoryId = BuiltInRegistries.STAT_TYPE.getId(type);
            if (categoryId == null) continue;
            captureType(handler, type, categoryId.toString(), snapshot);
        }

        if (snapshot.isEmpty()) return;

        final String key = info.address.toLowerCase();
        final String name = (info.name != null && !info.name.isEmpty() && !info.name.startsWith("Minecraft Server"))
                ? info.name : info.address;
        entries.put(key, new ServerEntry(name, snapshot));
        save();
    }

    private static <T> void captureType(final StatsCounter handler, final StatType<T> type, final String categoryStr,
                                        final Map<String, Map<String, Long>> snapshot) {
        final Registry<T> registry = type.getRegistry();
        final Map<String, Long> bucket = new LinkedHashMap<>();
        for (final Stat<T> stat : type) {
            final int v = handler.getStat(stat);
            if (v <= 0) continue;
            final ResourceLocation valueId = registry.getId(stat.getValue());
            if (valueId == null) continue;
            bucket.put(valueId.toString(), (long) v);
        }
        if (!bucket.isEmpty()) snapshot.put(categoryStr, bucket);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (final var serverEntry : root.entrySet()) {
                final JsonObject obj = serverEntry.getValue().getAsJsonObject();
                final String name = obj.has("name") ? obj.get("name").getAsString() : serverEntry.getKey();
                final Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
                if (obj.has("stats")) {
                    final JsonObject statsObj = obj.getAsJsonObject("stats");
                    for (final var cat : statsObj.entrySet()) {
                        final Map<String, Long> bucket = new LinkedHashMap<>();
                        for (final var stat : cat.getValue().getAsJsonObject().entrySet()) {
                            bucket.put(stat.getKey(), stat.getValue().getAsLong());
                        }
                        stats.put(cat.getKey(), bucket);
                    }
                }
                entries.put(serverEntry.getKey(), new ServerEntry(name, stats));
            }
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load server stats: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            final JsonObject root = new JsonObject();
            for (final Map.Entry<String, ServerEntry> e : entries.entrySet()) {
                final JsonObject obj = new JsonObject();
                obj.addProperty("name", e.getValue().displayName);
                obj.add("stats", GSON.toJsonTree(e.getValue().stats));
                root.add(e.getKey(), obj);
            }
            Files.writeString(file, GSON.toJson(root));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save server stats: {}", e.getMessage());
        }
    }
}
