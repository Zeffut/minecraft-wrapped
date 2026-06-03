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
        if (s == null || !enabled.getAsBoolean() || distinctId == null) return;
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
        if (s == null || distinctId == null) return;
        try {
            final Map<String, Object> merged = new HashMap<>(superProps);
            if (properties != null) merged.putAll(properties);
            s.capture(distinctId, event, merged);
            s.flush();
        } catch (final RuntimeException e) {
            LOGGER.debug("telemetry opt-out capture failed: {}", e.getMessage());
        }
    }

    public static void shutdown() {
        final TelemetrySink s = sink;
        if (s != null) {
            try {
                s.flush();
                s.close();
            } catch (final RuntimeException e) {
                LOGGER.debug("telemetry shutdown failed: {}", e.getMessage());
            }
        }
        sink = null;
        distinctId = null;
        enabled = () -> false;
        superProps = Map.of();
    }
}
