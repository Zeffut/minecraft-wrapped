package fr.zeffut.mcwrapped.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryTest {

    private static final class Recorded {
        final String distinctId;
        final String event;
        final Map<String, Object> props;
        Recorded(String d, String e, Map<String, Object> p) { distinctId = d; event = e; props = p; }
    }

    private static final class FakeSink implements TelemetrySink {
        final List<Recorded> captured = new ArrayList<>();
        boolean closed = false;
        @Override public void capture(String d, String e, Map<String, Object> p) { captured.add(new Recorded(d, e, p)); }
        @Override public void flush() {}
        @Override public void close() { closed = true; }
    }

    @AfterEach
    void reset() {
        Telemetry.shutdown();
    }

    @Test
    void capturesWhenEnabled() {
        final FakeSink sink = new FakeSink();
        Telemetry.init(sink, "uuid-123", () -> true, Map.of("app", "minecraft-wrapped"));

        Telemetry.capture("mcw_test", Map.of("k", "v"));

        assertEquals(1, sink.captured.size());
        final Recorded r = sink.captured.get(0);
        assertEquals("uuid-123", r.distinctId);
        assertEquals("mcw_test", r.event);
        assertEquals("v", r.props.get("k"));
        assertEquals("minecraft-wrapped", r.props.get("app"));
    }

    @Test
    void noOpWhenDisabled() {
        final FakeSink sink = new FakeSink();
        Telemetry.init(sink, "uuid-123", () -> false, Map.of("app", "minecraft-wrapped"));

        Telemetry.capture("mcw_test", Map.of());

        assertTrue(sink.captured.isEmpty());
    }

    @Test
    void noOpWhenNotInitialized() {
        Telemetry.capture("mcw_test", Map.of());
        // Only assertion: it did not throw.
    }

    @Test
    void swallowsSinkExceptions() {
        Telemetry.init(new TelemetrySink() {
            @Override public void capture(String d, String e, Map<String, Object> p) { throw new RuntimeException("boom"); }
            @Override public void flush() {}
            @Override public void close() {}
        }, "uuid-123", () -> true, Map.of());

        Telemetry.capture("mcw_test", Map.of()); // must not throw
    }
}
