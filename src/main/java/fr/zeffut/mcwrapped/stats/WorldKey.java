package fr.zeffut.mcwrapped.stats;

/**
 * Per-world / per-server play-time entries are keyed with a small prefix so the
 * UI can tell them apart and badge them: {@code sp:WorldName} or {@code srv:ServerName}.
 */
public final class WorldKey {

    public static final String SP_PREFIX = "sp:";
    public static final String SERVER_PREFIX = "srv:";

    private WorldKey() {}

    public static String singleplayer(final String name) { return SP_PREFIX + name; }
    public static String server(final String name) { return SERVER_PREFIX + name; }

    public static boolean isServer(final String key) { return key.startsWith(SERVER_PREFIX); }
    public static boolean isSingleplayer(final String key) { return key.startsWith(SP_PREFIX); }

    public static String displayName(final String key) {
        if (key.startsWith(SP_PREFIX)) return key.substring(SP_PREFIX.length());
        if (key.startsWith(SERVER_PREFIX)) return key.substring(SERVER_PREFIX.length());
        return key;
    }

    /**
     * Like {@link #displayName(String)} but honors {@code config.maskServerNames}: returns
     * "Server #N" for servers when the user opted into privacy masking. The numbering is stable as
     * long as the input list order is stable.
     */
    public static String displayNameMasked(final String key, final int serverIndex) {
        if (isServer(key) && fr.zeffut.mcwrapped.config.ConfigManager.get().maskServerNames) {
            return "Server #" + serverIndex;
        }
        return displayName(key);
    }

    public static String badge(final String key) {
        return isServer(key) ? "SERVER" : "WORLD";
    }
}
