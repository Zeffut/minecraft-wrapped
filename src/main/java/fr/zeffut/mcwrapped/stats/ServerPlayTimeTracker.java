package fr.zeffut.mcwrapped.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks cumulative ticks the player spends on each remote server, keyed by server address.
 *
 * <p>Vanilla play_time is server-side, so the client never sees its own time on multiplayer
 * servers. We count ticks ourselves while the client is connected and persist them to
 * {@code <gameDir>/wrapped/server-play-time.json}.
 */
public final class ServerPlayTimeTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAVE_INTERVAL_TICKS = 1200; // 1 minute

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private int ticksSinceLastSave = 0;

    public ServerPlayTimeTracker() {
        this.file = FabricLoader.getInstance().getGameDir().resolve("wrapped").resolve("server-play-time.json");
        load();
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> save());
    }

    private void onTick(final Minecraft client) {
        if (client.world == null || client.isInSingleplayer()) return;
        final ServerData info = client.getCurrentServerEntry();
        if (info == null || info.address == null) return;

        final String key = info.address.toLowerCase();
        final String name = (info.name != null && !info.name.isEmpty() && !info.name.startsWith("Minecraft Server"))
                ? info.name : info.address;
        entries.compute(key, (k, v) -> v == null ? new Entry(name, 1L) : new Entry(name, v.ticks + 1L));
        if (++ticksSinceLastSave >= SAVE_INTERVAL_TICKS) {
            save();
            ticksSinceLastSave = 0;
        }
    }

    /** Returns server play time aggregated by display name (suitable for merging into snapshots). */
    public Map<String, Long> playTimeByDisplayName() {
        final Map<String, Long> out = new HashMap<>();
        for (final Entry e : entries.values()) {
            out.merge(e.displayName, e.ticks, Long::sum);
        }
        return out;
    }

    public long totalTicks() {
        long total = 0;
        for (final Entry e : entries.values()) total += e.ticks;
        return total;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (final Map.Entry<String, JsonElement> entry : root.entrySet()) {
                final JsonObject obj = entry.getValue().getAsJsonObject();
                final String name = obj.has("name") ? obj.get("name").getAsString() : entry.getKey();
                final long ticks = obj.has("ticks") ? obj.get("ticks").getAsLong() : 0;
                entries.put(entry.getKey(), new Entry(name, ticks));
            }
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load server play time: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            final JsonObject root = new JsonObject();
            for (final Map.Entry<String, Entry> e : entries.entrySet()) {
                final JsonObject obj = new JsonObject();
                obj.addProperty("name", e.getValue().displayName);
                obj.addProperty("ticks", e.getValue().ticks);
                root.add(e.getKey(), obj);
            }
            Files.writeString(file, GSON.toJson(root));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save server play time: {}", e.getMessage());
        }
    }

    private record Entry(String displayName, long ticks) {}
}
