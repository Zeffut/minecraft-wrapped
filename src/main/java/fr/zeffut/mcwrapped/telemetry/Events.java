package fr.zeffut.mcwrapped.telemetry;

/**
 * Canonical event names. Every name carries the {@value #PREFIX} prefix so the mod's events
 * never collide with another app's events in the shared PostHog project.
 */
public final class Events {

    public static final String PREFIX = "mcw_";

    private Events() {}

    // Lifecycle & usage
    public static final String MOD_LOADED = PREFIX + "mod_loaded";
    public static final String WRAPPED_TRIGGERED = PREFIX + "wrapped_triggered";
    public static final String WRAPPED_STARTED = PREFIX + "wrapped_started";
    public static final String CARD_VIEWED = PREFIX + "card_viewed";
    public static final String WRAPPED_COMPLETED = PREFIX + "wrapped_completed";
    public static final String WRAPPED_SKIPPED = PREFIX + "wrapped_skipped";
    public static final String WRAPPED_READY_SHOWN = PREFIX + "wrapped_ready_shown";

    // Engagement
    public static final String IMAGE_SAVED = PREFIX + "image_saved";
    public static final String CLIPBOARD_COPIED = PREFIX + "clipboard_copied";
    public static final String COMMAND_USED = PREFIX + "command_used";
    public static final String HISTORY_OPENED = PREFIX + "history_opened";
    public static final String CONFIG_OPENED = PREFIX + "config_opened";
    public static final String CONFIG_CHANGED = PREFIX + "config_changed";

    // Gameplay (aggregated)
    public static final String WRAPPED_GENERATED = PREFIX + "wrapped_generated";

    // Errors & perf
    public static final String ERROR_CAUGHT = PREFIX + "error_caught";
    public static final String EXPORT_FAILED = PREFIX + "export_failed";
    public static final String WRAPPED_GENERATION_TIME = PREFIX + "wrapped_generation_time";
    public static final String ANIMATION_FPS = PREFIX + "animation_fps";

    // Consent
    public static final String TELEMETRY_OPT_IN = PREFIX + "telemetry_opt_in";
    public static final String TELEMETRY_OPT_OUT = PREFIX + "telemetry_opt_out";
}
