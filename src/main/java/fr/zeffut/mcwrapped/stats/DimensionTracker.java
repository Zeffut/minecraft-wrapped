package fr.zeffut.mcwrapped.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts ticks per dimension, bucketed by month. Used for the "Most explored dimension" card.
 */
public final class DimensionTracker {

    public static DimensionTracker INSTANCE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int SAVE_INTERVAL = 1200;

    private final Path file;
    /** month → dimension id → ticks */
    private final Map<String, Map<String, Long>> byMonth = new LinkedHashMap<>();
    private int saveCounter = 0;

    public DimensionTracker() {
        this.file = FabricLoader.getInstance().getGameDir().resolve("wrapped").resolve("play-by-dimension.json");
        load();
    }

    public void register() {
        INSTANCE = this;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(final MinecraftClient client) {
        if (client.world == null) return;
        final Identifier dimId = client.world.getRegistryKey().getValue();
        final String monthKey = YearMonth.now(ZoneId.systemDefault()).format(MONTH_FMT);
        byMonth.computeIfAbsent(monthKey, k -> new LinkedHashMap<>())
                .merge(dimId.toString(), 1L, Long::sum);
        if (++saveCounter >= SAVE_INTERVAL) {
            saveCounter = 0;
            save();
        }
    }

    public List<Map.Entry<String, Long>> topForMonth(final YearMonth month, final int limit) {
        final Map<String, Long> map = byMonth.getOrDefault(month.format(MONTH_FMT), Map.of());
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }

    public long totalTicksInMonth(final YearMonth month) {
        return byMonth.getOrDefault(month.format(MONTH_FMT), Map.of())
                .values().stream().mapToLong(Long::longValue).sum();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (final var monthEntry : root.entrySet()) {
                final Map<String, Long> bucket = new LinkedHashMap<>();
                for (final var dim : monthEntry.getValue().getAsJsonObject().entrySet()) {
                    bucket.put(dim.getKey(), dim.getValue().getAsLong());
                }
                byMonth.put(monthEntry.getKey(), bucket);
            }
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load dimension data: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(byMonth));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save dimension data: {}", e.getMessage());
        }
    }
}
