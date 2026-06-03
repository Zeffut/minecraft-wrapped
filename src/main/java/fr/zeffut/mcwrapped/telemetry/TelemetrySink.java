package fr.zeffut.mcwrapped.telemetry;

import java.util.Map;

/**
 * Abstraction over the telemetry backend. The real implementation wraps the PostHog SDK;
 * tests use an in-memory fake. Implementations must be safe to call from multiple threads
 * and must not throw from {@link #capture}.
 */
public interface TelemetrySink {

    void capture(String distinctId, String event, Map<String, Object> properties);

    void flush();

    void close();
}
