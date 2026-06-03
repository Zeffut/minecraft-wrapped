package fr.zeffut.mcwrapped.telemetry;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the super-properties attached to every telemetry event. The {@code app} tag isolates
 * this mod's data inside the shared PostHog project.
 */
public final class EventContext {

    /** Isolation tag — every event in the shared project carries this. */
    public static final String APP_TAG = "minecraft-wrapped";

    private EventContext() {}

    /** Pure builder — all inputs explicit so it is unit-testable. */
    public static Map<String, Object> buildSuperProps(
            final String modVersion, final String mcVersion,
            final String loaderVersion, final String fabricApiVersion,
            final String osName, final String osArch,
            final String javaVersion, final String language) {
        final Map<String, Object> props = new LinkedHashMap<>();
        props.put("app", APP_TAG);
        props.put("mod_version", modVersion);
        props.put("mc_version", mcVersion);
        props.put("fabric_loader_version", loaderVersion);
        props.put("fabric_api_version", fabricApiVersion);
        props.put("os_name", osName);
        props.put("os_arch", osArch);
        props.put("java_version", javaVersion);
        props.put("language", language);
        return props;
    }

    /** Collects super-properties from the live environment. Never throws. */
    public static Map<String, Object> collect() {
        return buildSuperProps(
                modVersion("mcwrapped"),
                modVersion("minecraft"),
                modVersion("fabricloader"),
                modVersion("fabric-api"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                System.getProperty("java.version", "unknown"),
                currentLanguage());
    }

    private static String modVersion(final String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String currentLanguage() {
        try {
            final MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && mc.options.language != null) {
                return mc.options.language;
            }
        } catch (final RuntimeException ignored) {
            // Fall through to default.
        }
        return "unknown";
    }
}
