package fr.zeffut.mcwrapped.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Static façade for telemetry. The only entry point the rest of the mod uses.
 *
 * <p>Strictly fire-and-forget: every {@link #capture} is a no-op when telemetry is disabled or
 * uninitialized, and never propagates exceptions. It must never block the client thread or break
 * the mod.
 */
public final class Telemetry {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcwrapped/telemetry");

    /** Telemetry is always off in a dev environment so local runs never pollute the dashboard. */
    private static final boolean DEV = isDevEnvironment();

    private static boolean isDevEnvironment() {
        try {
            return fr.zeffut.mcwrapped.platform.Platform.isDevelopment();
        } catch (final Throwable t) {
            return false;
        }
    }

    private static volatile TelemetrySink sink;
    private static volatile String distinctId;
    private static volatile BooleanSupplier enabled = () -> false;
    private static volatile Map<String, Object> superProps = Map.of();

    private Telemetry() {}

    /** Wires the façade. {@code enabledSupplier} is checked on every capture (live config read). */
    public static void init(final TelemetrySink newSink, final String newDistinctId,
                            final BooleanSupplier enabledSupplier, final Map<String, Object> newSuperProps) {
        sink = newSink;
        distinctId = newDistinctId;
        enabled = enabledSupplier;
        superProps = newSuperProps;
    }

    public static void capture(final String event, final Map<String, Object> properties) {
        final TelemetrySink s = sink;
        if (DEV || s == null || !enabled.getAsBoolean() || distinctId == null) return;
        try {
            final Map<String, Object> merged = new HashMap<>(superProps);
            if (properties != null) merged.putAll(properties);
            s.capture(distinctId, event, merged);
        } catch (final RuntimeException e) {
            LOGGER.debug("telemetry capture failed for {}: {}", event, e.getMessage());
        }
    }

    /** Convenience for events with no custom properties. */
    public static void capture(final String event) {
        capture(event, Map.of());
    }

    /**
     * Captures an event REGARDLESS of the enabled flag. Used only for the opt-out event, which we
     * want to record at the moment the user disables telemetry. Still a no-op if uninitialized.
     */
    public static void captureIgnoringConsent(final String event, final Map<String, Object> properties) {
        final TelemetrySink s = sink;
        if (DEV || s == null || distinctId == null) return;
        try {
            final Map<String, Object> merged = new HashMap<>(superProps);
            if (properties != null) merged.putAll(properties);
            s.capture(distinctId, event, merged);
            s.flush();
        } catch (final RuntimeException e) {
            LOGGER.debug("telemetry opt-out capture failed: {}", e.getMessage());
        }
    }

    /**
     * Same as {@link #capture} but tagging the event with an explicit {@code app} plus the standard
     * source / mc_version / component_version context. Used by the embedded auto-update module,
     * whose events are segmented under {@code app=autoupdate} (shared across every Zeffut host mod)
     * instead of this mod's own {@code app=minecraft-wrapped} slug. Same opt-out / dev / no-op
     * guarantees as {@link #capture}.
     */
    public static void captureForApp(final String app, final String event, final String source,
                                     final String mcVersion, final String modVersion,
                                     final Map<String, Object> properties) {
        final TelemetrySink s = sink;
        if (DEV || s == null || !enabled.getAsBoolean() || distinctId == null) return;
        try {
            final Map<String, Object> merged = new HashMap<>(superProps);
            merged.put("app", app);
            merged.put("source", source);
            merged.put("mc_version", mcVersion);
            merged.put("component_version", modVersion);
            if (properties != null) merged.putAll(properties);
            s.capture(distinctId, event, merged);
        } catch (final RuntimeException e) {
            LOGGER.debug("telemetry captureForApp failed for {}: {}", event, e.getMessage());
        }
    }

    /**
     * Emits {@code mcw_session_heartbeat} every 30 minutes (first beat after 5 minutes) on a daemon
     * scheduler so the dashboard can chart session length. Honors opt-out and the dev cutoff.
     */
    public static void startHeartbeat() {
        if (DEV) return;
        final long startedAt = System.currentTimeMillis();
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "mcwrapped-heartbeat");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> capture("mcw_session_heartbeat",
                Map.of("minutes_since_start", (System.currentTimeMillis() - startedAt) / 60_000)),
                5, 30, java.util.concurrent.TimeUnit.MINUTES);
    }

    public static void shutdown() {
        final TelemetrySink s = sink;
        // Stop accepting events immediately so nothing races on a half-closed sink.
        sink = null;
        distinctId = null;
        enabled = () -> false;
        superProps = Map.of();
        if (s == null) return;

        // Flush/close off the caller thread (client-stopping) and bound the wait, so a slow or
        // unreachable network can never hang game shutdown for more than a couple of seconds.
        final Thread t = new Thread(() -> {
            try {
                s.flush();
                s.close();
            } catch (final RuntimeException e) {
                LOGGER.debug("telemetry shutdown failed: {}", e.getMessage());
            }
        }, "mcwrapped-telemetry-shutdown");
        t.setDaemon(true);
        t.start();
        try {
            t.join(3000L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
