package fr.zeffut.mcwrapped.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts ticks per local hour-of-day, bucketed by month.
 */
public final class TimeOfDayTracker {

    public static TimeOfDayTracker INSTANCE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int SAVE_INTERVAL = 1200;

    private final Path file;
    /** month → 24-element array of ticks */
    private final Map<String, long[]> byMonth = new LinkedHashMap<>();
    private int saveCounter = 0;

    public TimeOfDayTracker() {
        this.file = FabricLoader.getInstance().getGameDir().resolve("wrapped").resolve("play-by-hour.json");
        load();
    }

    public void register() {
        INSTANCE = this;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(final MinecraftClient client) {
        // Only count when in a world (avoids inflating from idling on title screen).
        if (client.world == null) return;
        final LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        final String key = YearMonth.from(now).format(MONTH_FMT);
        final long[] arr = byMonth.computeIfAbsent(key, k -> new long[24]);
        arr[now.getHour()]++;
        if (++saveCounter >= SAVE_INTERVAL) {
            saveCounter = 0;
            save();
        }
    }

    public long[] forMonth(final YearMonth month) {
        return byMonth.getOrDefault(month.format(MONTH_FMT), new long[24]).clone();
    }

    public int peakHour(final YearMonth month) {
        final long[] arr = forMonth(month);
        int peak = 0;
        for (int i = 1; i < 24; i++) if (arr[i] > arr[peak]) peak = i;
        return peak;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (final var entry : root.entrySet()) {
                final var arrJson = entry.getValue().getAsJsonArray();
                final long[] arr = new long[24];
                for (int i = 0; i < 24 && i < arrJson.size(); i++) arr[i] = arrJson.get(i).getAsLong();
                byMonth.put(entry.getKey(), arr);
            }
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load hourly data: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            final JsonObject root = new JsonObject();
            for (final Map.Entry<String, long[]> entry : byMonth.entrySet()) {
                final var arr = new com.google.gson.JsonArray();
                for (final long v : entry.getValue()) arr.add(v);
                root.add(entry.getKey(), arr);
            }
            Files.writeString(file, GSON.toJson(root));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save hourly data: {}", e.getMessage());
        }
    }
}
