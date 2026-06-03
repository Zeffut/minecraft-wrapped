package fr.zeffut.mcwrapped.telemetry;

import com.posthog.server.PostHog;
import com.posthog.server.PostHogCaptureOptions;
import com.posthog.server.PostHogConfig;
import com.posthog.server.PostHogInterface;

import java.util.Map;

/**
 * {@link TelemetrySink} backed by the PostHog Java SDK. The SDK queues and flushes asynchronously,
 * so {@link #capture} is non-blocking.
 *
 * <p>Project key is a public write-only token (safe to embed). Data lands in the shared
 * "Default project"; the {@code app} super-property isolates this mod's events.
 *
 * <p>NOTE: posthog-server is beta. If the resolved version's API differs from the
 * {@code capture(distinctId, event, Map)} / {@code PostHog.with(config)} shape used here,
 * adapt THIS class only.
 */
public final class PostHogTelemetrySink implements TelemetrySink {

    private static final String API_KEY = "phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359";
    // TODO confirm EU vs US against the PostHog install snippet before release.
    private static final String HOST = "https://eu.i.posthog.com";

    private final PostHogInterface posthog;

    public PostHogTelemetrySink() {
        final PostHogConfig config = new PostHogConfig.Builder(API_KEY).host(HOST).build();
        this.posthog = PostHog.with(config);
    }

    @Override
    public void capture(final String distinctId, final String event, final Map<String, Object> properties) {
        final PostHogCaptureOptions options = PostHogCaptureOptions.builder()
                .properties(properties)
                .build();
        posthog.capture(distinctId, event, options);
    }

    @Override
    public void flush() {
        posthog.flush();
    }

    @Override
    public void close() {
        posthog.close();
    }
}
