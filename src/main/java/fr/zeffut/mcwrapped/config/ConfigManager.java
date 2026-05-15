package fr.zeffut.mcwrapped.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and persists the single global {@link McWrappedConfig} from
 * {@code config/mcwrapped.json}. Thread-confined to the client thread.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcwrapped.json";

    private static ConfigManager INSTANCE;

    private final Path file;
    private McWrappedConfig config;

    private ConfigManager(final Path file) {
        this.file = file;
        this.config = load();
    }

    /** Initializes the singleton using the standard Fabric config directory. Idempotent. */
    public static synchronized ConfigManager init() {
        if (INSTANCE == null) {
            final Path dir = FabricLoader.getInstance().getConfigDir();
            INSTANCE = new ConfigManager(dir.resolve(FILE_NAME));
        }
        return INSTANCE;
    }

    /** Returns the live config object. Mutate freely; call {@link #save()} to persist. */
    public static McWrappedConfig get() {
        return init().config;
    }

    public static void save() {
        init().writeToDisk();
    }

    private McWrappedConfig load() {
        if (!Files.exists(file)) {
            final McWrappedConfig fresh = new McWrappedConfig();
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(fresh));
            } catch (final IOException e) {
                McWrappedClient.LOGGER.warn("Failed to write default config: {}", e.getMessage());
            }
            return fresh;
        }
        try {
            final String text = Files.readString(file);
            final McWrappedConfig parsed = GSON.fromJson(text, McWrappedConfig.class);
            if (parsed == null) return new McWrappedConfig();
            // Backfill null fields that may exist on older config files.
            return sanitize(parsed);
        } catch (final IOException | RuntimeException e) {
            McWrappedClient.LOGGER.warn("Config load failed ({}), using defaults.", e.getMessage());
            return new McWrappedConfig();
        }
    }

    private void writeToDisk() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save config: {}", e.getMessage());
        }
    }

    /** Defensive: replace any null collection / enum that older configs may have stored as null. */
    private static McWrappedConfig sanitize(final McWrappedConfig c) {
        final McWrappedConfig d = new McWrappedConfig();
        if (c.theme == null) c.theme = d.theme;
        if (c.gradient == null) c.gradient = d.gradient;
        if (c.enabledCards == null) c.enabledCards = d.enabledCards;
        if (c.cardOrder == null) c.cardOrder = d.cardOrder;
        if (c.targetMonth == null) c.targetMonth = "";
        if (c.includedWorlds == null) c.includedWorlds = d.includedWorlds;
        if (c.excludedWorlds == null) c.excludedWorlds = d.excludedWorlds;
        if (c.customTitle == null) c.customTitle = "";
        if (c.sparkleDensity == null) c.sparkleDensity = d.sparkleDensity;
        if (c.transition == null) c.transition = d.transition;
        if (c.aspectRatio == null) c.aspectRatio = d.aspectRatio;
        if (c.signature == null) c.signature = "";
        // Numeric clamps to keep older garbage inside the supported range.
        c.speedMultiplier = clamp(c.speedMultiplier, 0f, 2f);
        c.soundVolume = (int) clamp(c.soundVolume, 0, 100);
        c.autoTriggerGraceDays = (int) clamp(c.autoTriggerGraceDays, 1, 31);
        // Re-fill missing CardIds with default enabled=true.
        for (final CardId id : CardId.values()) {
            c.enabledCards.putIfAbsent(id, Boolean.TRUE);
            if (!c.cardOrder.contains(id)) c.cardOrder.add(id);
        }
        return c;
    }

    private static float clamp(final float v, final float min, final float max) {
        return Math.max(min, Math.min(max, v));
    }
}
