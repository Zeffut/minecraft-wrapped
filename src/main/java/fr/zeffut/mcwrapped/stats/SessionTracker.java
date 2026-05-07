package fr.zeffut.mcwrapped.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a session boundary every time the game starts and stops.
 */
public final class SessionTracker {

    public static SessionTracker INSTANCE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final List<Session> sessions = new ArrayList<>();
    private long sessionStartMs = -1;

    public record Session(long startMs, long endMs) {
        public long durationMs() { return Math.max(0, endMs - startMs); }
        public YearMonth endMonth() { return YearMonth.from(LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), ZoneId.systemDefault())); }
    }

    public SessionTracker() {
        this.file = FabricLoader.getInstance().getGameDir().resolve("wrapped").resolve("sessions.json");
        load();
    }

    public void register() {
        INSTANCE = this;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            sessionStartMs = System.currentTimeMillis();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (sessionStartMs > 0) {
                sessions.add(new Session(sessionStartMs, System.currentTimeMillis()));
                save();
            }
        });
    }

    public long longestSessionMsInMonth(final YearMonth month) {
        long max = 0;
        for (final Session s : sessions) {
            if (s.endMonth().equals(month) && s.durationMs() > max) max = s.durationMs();
        }
        return max;
    }

    public int sessionCountInMonth(final YearMonth month) {
        int count = 0;
        for (final Session s : sessions) if (s.endMonth().equals(month)) count++;
        return count;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.has("sessions")) return;
            for (final var el : root.getAsJsonArray("sessions")) {
                final JsonObject obj = el.getAsJsonObject();
                sessions.add(new Session(obj.get("start").getAsLong(), obj.get("end").getAsLong()));
            }
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load sessions: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            final JsonObject root = new JsonObject();
            final JsonArray arr = new JsonArray();
            for (final Session s : sessions) {
                final JsonObject obj = new JsonObject();
                obj.addProperty("start", s.startMs);
                obj.addProperty("end", s.endMs);
                arr.add(obj);
            }
            root.add("sessions", arr);
            Files.writeString(file, GSON.toJson(root));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save sessions: {}", e.getMessage());
        }
    }
}
