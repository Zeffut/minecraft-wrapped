package fr.zeffut.mcwrapped.config;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Disk-fresh reader for the auto-update options inside {@code config/mcwrapped.json}. The embedded
 * {@code update.UpdateService} runs on a background thread well after config load, so it reads each
 * option straight from disk here (rather than the cached {@link ConfigManager} instance) to honor
 * live edits the player may have made. All failures fall back to the supplied default.
 *
 * <p>Recognized keys: {@code auto_update} (default {@code true}), {@code update_owner}
 * (default {@code Zeffut}), {@code update_all} (default {@code false}), {@code update_exclude}
 * (default empty), mapped onto the {@link McWrappedConfig} fields.
 */
public final class AutoUpdateSettings {

    private static final Gson GSON = new Gson();

    private AutoUpdateSettings() {}

    public static String setting(final String key, final String fallback) {
        try {
            final Path file = FabricLoader.getInstance().getConfigDir().resolve("mcwrapped.json");
            if (!Files.exists(file)) return fallback;
            final McWrappedConfig c = GSON.fromJson(Files.readString(file), McWrappedConfig.class);
            if (c == null) return fallback;
            return switch (key) {
                case "auto_update" -> String.valueOf(c.autoUpdate);
                case "update_owner" -> c.updateOwner == null || c.updateOwner.isBlank()
                        ? fallback : c.updateOwner;
                case "update_all" -> String.valueOf(c.updateAll);
                case "update_exclude" -> c.updateExclude == null ? fallback : c.updateExclude;
                default -> fallback;
            };
        } catch (final Throwable t) {
            return fallback;
        }
    }
}
