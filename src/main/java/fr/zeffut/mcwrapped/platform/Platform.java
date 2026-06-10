package fr.zeffut.mcwrapped.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Tiny loader-abstraction layer for the embedded auto-update module. Mapping-agnostic: references
 * no {@code net.minecraft} class (only the stable Fabric loader API), so it compiles identically
 * across every supported Minecraft version branch of this Fabric-only mod.
 *
 * <p>Provides loader name, mod/MC version, dev-environment flag and the game/config directories.
 */
public final class Platform {

    /** Mod id of the host mod embedding the auto-update module. */
    private static final String HOST_MOD_ID = "mcwrapped";

    private Platform() {}

    /** Always {@code "fabric"} for this Fabric-only mod. */
    public static String loader() {
        return "fabric";
    }

    /** Friendly version string of this mod, or {@code "unknown"}. */
    public static String modVersion() {
        return FabricLoader.getInstance().getModContainer(HOST_MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    /** Friendly version string of Minecraft, or {@code "unknown"}. */
    public static String mcVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    /** True if running in a development environment (so telemetry stays off locally). */
    public static boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /** Absolute path to the Minecraft instance/run directory. */
    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    /** Absolute path to the {@code config/} directory. */
    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
