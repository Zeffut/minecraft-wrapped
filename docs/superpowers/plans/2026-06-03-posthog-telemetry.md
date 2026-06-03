# PostHog Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-out PostHog telemetry system to Minecraft Wrapped that captures lifecycle, engagement, aggregated gameplay, and error/perf events, isolated in a shared PostHog project via an `app` tag and `mcw_` event prefix.

**Architecture:** A self-contained `fr.zeffut.mcwrapped.telemetry` package. A static `Telemetry` façade is the only entry point the rest of the mod touches; it delegates to a `TelemetrySink` interface (real impl `PostHogTelemetrySink` wraps the `posthog-server` SDK; tests use a fake). Every event is fire-and-forget: no-op when disabled/uninitialized, never throws, never blocks the client thread. Super-properties (incl. the `app` tag) are merged into every event. The SDK is bundled via Shadow with package relocation to avoid classpath conflicts with other mods.

**Tech Stack:** Java 21, Fabric Loom 1.13, Gradle, `com.posthog:posthog-server:2.+`, `com.gradleup.shadow`, JUnit 5.

---

## Important context for the implementer

- **PostHog project is SHARED.** Project id `192659` ("Default project") also hosts another app. Isolation relies on (1) super-property `app="minecraft-wrapped"` on every event and (2) the `mcw_` prefix on every event name. Never emit an event without going through `Events`/`Telemetry`, which guarantee both.
- **Project API key (public, write-only, safe to embed):** `phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359`
- **Ingestion host:** `https://eu.i.posthog.com` — *confirm EU vs US* against the PostHog install snippet before shipping. It is a single constant in `PostHogTelemetrySink`.
- **distinct_id:** the raw Minecraft account UUID (`MinecraftClient.getSession().getUuidOrNull().toString()`). This is the user's explicit choice. IP anonymization is enabled project-side.
- **SDK is beta.** The exact `posthog-server` package and method signatures (esp. the `capture` overload that takes a properties map) MUST be verified against the version Gradle resolves. The docs show `com.posthog.server.PostHog` / `PostHogConfig` / `PostHogInterface` and `posthog.capture(distinctId, event, Map<String,Object>)`. If the resolved API differs, adapt `PostHogTelemetrySink` only — nothing else depends on the SDK.
- **No test infra exists yet.** Task 1 adds JUnit 5 and a `src/test/java` source set.
- **Thread-safety:** `Telemetry.capture` may be called from the client thread and from export worker threads (`mcwrapped-copy`, `mcwrapped-export`). The sink must tolerate concurrent calls (the SDK queues internally; our wrapper just forwards).

---

## File Structure

**New files:**
- `src/main/java/fr/zeffut/mcwrapped/telemetry/Telemetry.java` — static façade, no-op guard, super-prop merge.
- `src/main/java/fr/zeffut/mcwrapped/telemetry/TelemetrySink.java` — interface (`capture`, `flush`, `close`).
- `src/main/java/fr/zeffut/mcwrapped/telemetry/PostHogTelemetrySink.java` — real SDK-backed sink.
- `src/main/java/fr/zeffut/mcwrapped/telemetry/Events.java` — event-name constants (prefixed) + small prop-map builders.
- `src/main/java/fr/zeffut/mcwrapped/telemetry/EventContext.java` — builds super-properties.
- `src/main/java/fr/zeffut/mcwrapped/telemetry/StatsBucketer.java` — pure bucketing of playtime/deaths/distance.
- `src/main/java/fr/zeffut/mcwrapped/ui/WrappedLauncher.java` — single chokepoint to open a wrapped + emit trigger/started/generated.
- `src/test/java/fr/zeffut/mcwrapped/telemetry/StatsBucketerTest.java`
- `src/test/java/fr/zeffut/mcwrapped/telemetry/EventContextTest.java`
- `src/test/java/fr/zeffut/mcwrapped/telemetry/TelemetryTest.java`

**Modified files:**
- `build.gradle` — shadow plugin, posthog dep + relocation, JUnit, test task.
- `gradle.properties` — posthog version property.
- `src/main/java/fr/zeffut/mcwrapped/config/McWrappedConfig.java` — `telemetryEnabled` field.
- `src/main/java/fr/zeffut/mcwrapped/config/ui/McWrappedConfigScreen.java` — toggle + `config_opened`/`config_changed`.
- `src/main/java/fr/zeffut/mcwrapped/McWrappedClient.java` — init/shutdown, `mod_loaded`, `wrapped_generation_time`.
- `src/main/java/fr/zeffut/mcwrapped/command/WrappedCommand.java` — route opens through `WrappedLauncher`, `command_used`, `history_opened`, telemetry subcommand.
- `src/main/java/fr/zeffut/mcwrapped/ui/WrappedTitleButton.java` — route open through `WrappedLauncher`, `wrapped_ready_shown`.
- `src/main/java/fr/zeffut/mcwrapped/ui/WrappedCardScreen.java` — `card_viewed`, `wrapped_completed`, `wrapped_skipped`, `animation_fps`.
- `src/main/java/fr/zeffut/mcwrapped/ui/cards/Card.java` — `analyticsId()` default method.
- `src/main/java/fr/zeffut/mcwrapped/ui/cards/FinalCard.java` — `image_saved`, `clipboard_copied`, `export_failed`.
- `src/main/java/fr/zeffut/mcwrapped/ui/cards/WrappedContext.java` — `totalDistanceCm()` + `contextType()` helpers.
- `README.md` and `CLAUDE.md` — privacy notice + decision update.

---

## Task 1: Build setup — Shadow, posthog-server, JUnit

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Create: `src/test/java/fr/zeffut/mcwrapped/SmokeTest.java`

- [ ] **Step 1: Add version property**

In `gradle.properties`, under `# Config dependencies`, add:

```properties
posthog_version=2.+
```

- [ ] **Step 2: Add the shadow plugin**

In `build.gradle`, change the `plugins` block to:

```gradle
plugins {
    id 'fabric-loom' version '1.13-SNAPSHOT'
    id 'maven-publish'
    id 'com.gradleup.shadow' version '8.3.5'
}
```

- [ ] **Step 3: Add a `shadowBundle` configuration and the posthog dependency**

In `build.gradle`, replace the `dependencies { ... }` block with:

```gradle
configurations {
    // Library jars we want shaded into the mod jar (not Fabric mods).
    shadowBundle
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    // ModMenu surfaces our custom config screen from the in-game mods list.
    modImplementation "com.terraformersmc:modmenu:${project.modmenu_version}"

    // PostHog telemetry SDK — compiled against, and bundled (relocated) into the jar.
    implementation "com.posthog:posthog-server:${project.posthog_version}"
    shadowBundle "com.posthog:posthog-server:${project.posthog_version}"

    // Tests.
    testImplementation platform("org.junit:junit-bom:5.11.3")
    testImplementation "org.junit.jupiter:junit-jupiter"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
}
```

- [ ] **Step 4: Configure shadowJar relocation and wire it into remapJar**

In `build.gradle`, after the `loom { ... }` block, add:

```gradle
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

shadowJar {
    configurations = [project.configurations.shadowBundle]
    archiveClassifier = 'dev-shadow'
    // Relocate every bundled package under our namespace so two mods can't clash on
    // shared libs (gson/okhttp/etc.). Verify the real transitive packages with
    // `./gradlew dependencies --configuration shadowBundle` and add any that are missing.
    relocate 'com.posthog', 'fr.zeffut.mcwrapped.shadow.posthog'
    relocate 'okhttp3', 'fr.zeffut.mcwrapped.shadow.okhttp3'
    relocate 'okio', 'fr.zeffut.mcwrapped.shadow.okio'
    relocate 'org.json', 'fr.zeffut.mcwrapped.shadow.json'
}

tasks.named('remapJar') {
    dependsOn shadowJar
    inputFile = shadowJar.archiveFile
}
```

- [ ] **Step 5: Enable the JUnit test task**

In `build.gradle`, after the `java { ... }` block, add:

```gradle
test {
    useJUnitPlatform()
}
```

- [ ] **Step 6: Add a smoke test**

Create `src/test/java/fr/zeffut/mcwrapped/SmokeTest.java`:

```java
package fr.zeffut.mcwrapped;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTest {
    @Test
    void junitRuns() {
        assertTrue(true);
    }
}
```

- [ ] **Step 7: Verify the build and tests**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, `SmokeTest` passes.

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. Confirm the remapped jar in `build/libs/` is the shadowed one (no `dev-shadow` classifier on the final `minecraft-wrapped-<version>.jar`).

- [ ] **Step 8: Inspect the bundled dependency tree (verification, may require fixing Step 4)**

Run: `./gradlew dependencies --configuration shadowBundle`
Read the tree. For every third-party top-level package present (besides `com.posthog`), ensure there is a matching `relocate` line in Step 4. If the SDK pulls e.g. `com.squareup.moshi` or kotlin stdlib, add `relocate` lines for them and re-run Step 7.

- [ ] **Step 9: Commit**

```bash
git add gradle.properties build.gradle src/test/java/fr/zeffut/mcwrapped/SmokeTest.java
git commit -m "build: add posthog-server (shaded) + JUnit test infra"
```

---

## Task 2: StatsBucketer (pure, TDD)

**Files:**
- Create: `src/main/java/fr/zeffut/mcwrapped/telemetry/StatsBucketer.java`
- Test: `src/test/java/fr/zeffut/mcwrapped/telemetry/StatsBucketerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/fr/zeffut/mcwrapped/telemetry/StatsBucketerTest.java`:

```java
package fr.zeffut.mcwrapped.telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsBucketerTest {

    private static final long TICKS_PER_HOUR = 20L * 60 * 60;

    @Test
    void playtimeBuckets() {
        assertEquals("<1h", StatsBucketer.playtimeBucket(0));
        assertEquals("<1h", StatsBucketer.playtimeBucket(TICKS_PER_HOUR - 1));
        assertEquals("1-10h", StatsBucketer.playtimeBucket(TICKS_PER_HOUR));
        assertEquals("1-10h", StatsBucketer.playtimeBucket(10 * TICKS_PER_HOUR - 1));
        assertEquals("10-50h", StatsBucketer.playtimeBucket(10 * TICKS_PER_HOUR));
        assertEquals("50-100h", StatsBucketer.playtimeBucket(50 * TICKS_PER_HOUR));
        assertEquals("100h+", StatsBucketer.playtimeBucket(100 * TICKS_PER_HOUR));
    }

    @Test
    void deathsBuckets() {
        assertEquals("0", StatsBucketer.deathsBucket(0));
        assertEquals("1-5", StatsBucketer.deathsBucket(1));
        assertEquals("1-5", StatsBucketer.deathsBucket(5));
        assertEquals("6-20", StatsBucketer.deathsBucket(6));
        assertEquals("21-50", StatsBucketer.deathsBucket(21));
        assertEquals("50+", StatsBucketer.deathsBucket(51));
    }

    @Test
    void distanceBuckets() {
        // input is centimeters
        assertEquals("<10km", StatsBucketer.distanceBucket(0));
        assertEquals("<10km", StatsBucketer.distanceBucket(10L * 1000 * 100 - 1));
        assertEquals("10-50km", StatsBucketer.distanceBucket(10L * 1000 * 100));
        assertEquals("50-100km", StatsBucketer.distanceBucket(50L * 1000 * 100));
        assertEquals("100-500km", StatsBucketer.distanceBucket(100L * 1000 * 100));
        assertEquals("500km+", StatsBucketer.distanceBucket(500L * 1000 * 100));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'fr.zeffut.mcwrapped.telemetry.StatsBucketerTest'`
Expected: FAIL — `StatsBucketer` does not exist (compile error).

- [ ] **Step 3: Implement `StatsBucketer`**

Create `src/main/java/fr/zeffut/mcwrapped/telemetry/StatsBucketer.java`:

```java
package fr.zeffut.mcwrapped.telemetry;

/**
 * Maps raw stat values to coarse string buckets so the telemetry never carries
 * re-identifying exact numbers. Pure functions, no dependencies.
 */
public final class StatsBucketer {

    private static final long TICKS_PER_HOUR = 20L * 60 * 60;
    private static final long CM_PER_KM = 1000L * 100;

    private StatsBucketer() {}

    public static String playtimeBucket(final long playTimeTicks) {
        final long hours = playTimeTicks / TICKS_PER_HOUR;
        if (hours < 1) return "<1h";
        if (hours < 10) return "1-10h";
        if (hours < 50) return "10-50h";
        if (hours < 100) return "50-100h";
        return "100h+";
    }

    public static String deathsBucket(final long deaths) {
        if (deaths <= 0) return "0";
        if (deaths <= 5) return "1-5";
        if (deaths <= 20) return "6-20";
        if (deaths <= 50) return "21-50";
        return "50+";
    }

    public static String distanceBucket(final long distanceCm) {
        final long km = distanceCm / CM_PER_KM;
        if (km < 10) return "<10km";
        if (km < 50) return "10-50km";
        if (km < 100) return "50-100km";
        if (km < 500) return "100-500km";
        return "500km+";
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests 'fr.zeffut.mcwrapped.telemetry.StatsBucketerTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/telemetry/StatsBucketer.java src/test/java/fr/zeffut/mcwrapped/telemetry/StatsBucketerTest.java
git commit -m "feat(telemetry): add StatsBucketer for coarse stat buckets"
```

---

## Task 3: EventContext super-properties (pure core, TDD)

**Files:**
- Create: `src/main/java/fr/zeffut/mcwrapped/telemetry/EventContext.java`
- Test: `src/test/java/fr/zeffut/mcwrapped/telemetry/EventContextTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/fr/zeffut/mcwrapped/telemetry/EventContextTest.java`:

```java
package fr.zeffut.mcwrapped.telemetry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventContextTest {

    @Test
    void buildSuperPropsCarriesAppTagAndVersions() {
        final Map<String, Object> props = EventContext.buildSuperProps(
                "1.1.0", "1.21.11", "0.19.2", "0.141.3+1.21.11",
                "Mac OS X", "aarch64", "21.0.2", "fr_fr");

        assertEquals("minecraft-wrapped", props.get("app"));
        assertEquals("1.1.0", props.get("mod_version"));
        assertEquals("1.21.11", props.get("mc_version"));
        assertEquals("0.19.2", props.get("fabric_loader_version"));
        assertEquals("0.141.3+1.21.11", props.get("fabric_api_version"));
        assertEquals("Mac OS X", props.get("os_name"));
        assertEquals("aarch64", props.get("os_arch"));
        assertEquals("21.0.2", props.get("java_version"));
        assertEquals("fr_fr", props.get("language"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'fr.zeffut.mcwrapped.telemetry.EventContextTest'`
Expected: FAIL — `EventContext` does not exist.

- [ ] **Step 3: Implement `EventContext`**

Create `src/main/java/fr/zeffut/mcwrapped/telemetry/EventContext.java`:

```java
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
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests 'fr.zeffut.mcwrapped.telemetry.EventContextTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/telemetry/EventContext.java src/test/java/fr/zeffut/mcwrapped/telemetry/EventContextTest.java
git commit -m "feat(telemetry): add EventContext super-properties with app tag"
```

---

## Task 4: TelemetrySink interface + Events constants

**Files:**
- Create: `src/main/java/fr/zeffut/mcwrapped/telemetry/TelemetrySink.java`
- Create: `src/main/java/fr/zeffut/mcwrapped/telemetry/Events.java`

- [ ] **Step 1: Create the `TelemetrySink` interface**

Create `src/main/java/fr/zeffut/mcwrapped/telemetry/TelemetrySink.java`:

```java
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
```

- [ ] **Step 2: Create the `Events` constants**

Create `src/main/java/fr/zeffut/mcwrapped/telemetry/Events.java`:

```java
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
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/telemetry/TelemetrySink.java src/main/java/fr/zeffut/mcwrapped/telemetry/Events.java
git commit -m "feat(telemetry): add TelemetrySink interface and Events constants"
```

---

## Task 5: Telemetry façade (no-op guard, TDD)

**Files:**
- Create: `src/main/java/fr/zeffut/mcwrapped/telemetry/Telemetry.java`
- Test: `src/test/java/fr/zeffut/mcwrapped/telemetry/TelemetryTest.java`

Behavior: `Telemetry` holds a static sink + distinctId + an `enabled` supplier. `capture` does nothing when no sink is set or when `enabled` returns false, merges super-props into the event otherwise, and never propagates exceptions.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/fr/zeffut/mcwrapped/telemetry/TelemetryTest.java`:

```java
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
        // super-props merged in
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
        // No init() called after reset.
        Telemetry.capture("mcw_test", Map.of());
        // Nothing to assert other than: it did not throw.
    }

    @Test
    void swallowsSinkExceptions() {
        Telemetry.init(new TelemetrySink() {
            @Override public void capture(String d, String e, Map<String, Object> p) { throw new RuntimeException("boom"); }
            @Override public void flush() {}
            @Override public void close() {}
        }, "uuid-123", () -> true, Map.of());

        // Must not throw.
        Telemetry.capture("mcw_test", Map.of());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'fr.zeffut.mcwrapped.telemetry.TelemetryTest'`
Expected: FAIL — `Telemetry` does not exist.

- [ ] **Step 3: Implement `Telemetry`**

Create `src/main/java/fr/zeffut/mcwrapped/telemetry/Telemetry.java`:

```java
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
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests 'fr.zeffut.mcwrapped.telemetry.TelemetryTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/telemetry/Telemetry.java src/test/java/fr/zeffut/mcwrapped/telemetry/TelemetryTest.java
git commit -m "feat(telemetry): add Telemetry façade with no-op guard"
```

---

## Task 6: PostHogTelemetrySink (real SDK wrapper)

**Files:**
- Create: `src/main/java/fr/zeffut/mcwrapped/telemetry/PostHogTelemetrySink.java`

No unit test (network/SDK). Verified by compile + in-game Live Events later.

- [ ] **Step 1: Implement the sink**

Create `src/main/java/fr/zeffut/mcwrapped/telemetry/PostHogTelemetrySink.java`:

```java
package fr.zeffut.mcwrapped.telemetry;

import com.posthog.server.PostHog;
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
        final PostHogConfig config = PostHogConfig.builder(API_KEY).host(HOST).build();
        this.posthog = PostHog.with(config);
    }

    @Override
    public void capture(final String distinctId, final String event, final Map<String, Object> properties) {
        posthog.capture(distinctId, event, properties);
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
```

- [ ] **Step 2: Verify it compiles against the resolved SDK**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. If it fails on the SDK API (method/class names), open the resolved `posthog-server` jar (`./gradlew dependencies --configuration shadowBundle` shows the version) and adjust imports/signatures to match, then re-run.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/telemetry/PostHogTelemetrySink.java
git commit -m "feat(telemetry): add PostHog SDK-backed sink"
```

---

## Task 7: Config field + consent wiring

**Files:**
- Modify: `src/main/java/fr/zeffut/mcwrapped/config/McWrappedConfig.java`

- [ ] **Step 1: Add the `telemetryEnabled` field**

In `McWrappedConfig.java`, in the `// ---- E. Privacy` section (after `maskIgn`), add:

```java
    /** Opt-out telemetry. Enabled by default; user can disable it in the config screen. */
    public boolean telemetryEnabled = true;
```

- [ ] **Step 2: Reset it in `resetToDefaults()`**

In `resetToDefaults()`, after `this.maskIgn = d.maskIgn;`, add:

```java
        this.telemetryEnabled = d.telemetryEnabled;
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

Note: no `sanitize()` change needed — Gson keeps the field initializer (`true`) for config files that predate this key, which is exactly the opt-out default.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/config/McWrappedConfig.java
git commit -m "feat(telemetry): add telemetryEnabled config field (opt-out default)"
```

---

## Task 8: Initialize telemetry at startup + mod_loaded + generation timing

**Files:**
- Modify: `src/main/java/fr/zeffut/mcwrapped/McWrappedClient.java`

- [ ] **Step 1: Add imports**

In `McWrappedClient.java`, add to the import block:

```java
import fr.zeffut.mcwrapped.config.McWrappedConfig;
import fr.zeffut.mcwrapped.telemetry.EventContext;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.PostHogTelemetrySink;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import net.minecraft.client.session.Session;

import java.util.HashMap;
import java.util.Map;
```

- [ ] **Step 2: Initialize telemetry inside `onInitializeClient`**

In `onInitializeClient()`, after `ConfigManager.init();`, add:

```java
        initTelemetry();
```

And register a shutdown hook — after the existing `ClientLifecycleEvents.CLIENT_STARTED.register(...)` line, add:

```java
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Telemetry.shutdown());
```

- [ ] **Step 3: Add the `initTelemetry` method**

Add this private method to `McWrappedClient`:

```java
    /**
     * Wires the telemetry façade. Runs the SDK init off-thread so a slow network never delays boot.
     * distinct_id is the raw Minecraft account UUID (user's explicit choice; IPs anonymized
     * project-side).
     */
    private void initTelemetry() {
        new Thread(() -> {
            try {
                final Session session = MinecraftClient.getInstance().getSession();
                final String uuid = session != null && session.getUuidOrNull() != null
                        ? session.getUuidOrNull().toString()
                        : "anonymous";
                final Map<String, Object> superProps = EventContext.collect();
                Telemetry.init(new PostHogTelemetrySink(), uuid,
                        () -> ConfigManager.get().telemetryEnabled, superProps);

                final Map<String, Object> props = new HashMap<>();
                props.put("history_count", snapshots.listWrapped().size());
                Telemetry.capture(Events.MOD_LOADED, props);
            } catch (final RuntimeException e) {
                LOGGER.debug("Telemetry init skipped: {}", e.getMessage());
            }
        }, "mcwrapped-telemetry-init").start();
    }
```

- [ ] **Step 4: Time the wrapped generation in `captureAndFinalize`**

In `captureAndFinalize`, replace the finalize block:

```java
        final MonthlyDelta delta = MonthlyDelta.compute(prev, current);
        snapshots.saveWrapped(new WrappedFile(prev.month(), delta, false));
        LOGGER.info("Wrapped ready for {} — a button will appear on the title screen.", prev.month());
```

with:

```java
        final long t0 = System.nanoTime();
        final MonthlyDelta delta = MonthlyDelta.compute(prev, current);
        final long genMs = (System.nanoTime() - t0) / 1_000_000L;
        snapshots.saveWrapped(new WrappedFile(prev.month(), delta, false));
        Telemetry.capture(Events.WRAPPED_GENERATION_TIME, Map.of("duration_ms", genMs));
        LOGGER.info("Wrapped ready for {} — a button will appear on the title screen.", prev.month());
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/McWrappedClient.java
git commit -m "feat(telemetry): init at startup, mod_loaded + generation timing"
```

---

## Task 9: WrappedContext helpers + Card.analyticsId

**Files:**
- Modify: `src/main/java/fr/zeffut/mcwrapped/ui/cards/WrappedContext.java`
- Modify: `src/main/java/fr/zeffut/mcwrapped/ui/cards/Card.java`

- [ ] **Step 1: Add `totalDistanceCm()` and `contextType()` to `WrappedContext`**

In `WrappedContext.java`, after the `jumps()` method, add:

```java
    /** Sum of all movement custom stats, in centimeters. */
    public long totalDistanceCm() {
        final Map<String, Long> c = custom();
        return c.getOrDefault("minecraft:walk_one_cm", 0L)
                + c.getOrDefault("minecraft:sprint_one_cm", 0L)
                + c.getOrDefault("minecraft:boat_one_cm", 0L)
                + c.getOrDefault("minecraft:aviate_one_cm", 0L)
                + c.getOrDefault("minecraft:horse_one_cm", 0L)
                + c.getOrDefault("minecraft:fly_one_cm", 0L)
                + c.getOrDefault("minecraft:swim_one_cm", 0L);
    }

    /** "server" if most play time was on servers this month, else "singleplayer". */
    public String contextType() {
        return serverTicks() > soloTicks() ? "server" : "singleplayer";
    }
```

- [ ] **Step 2: Add `analyticsId()` to the `Card` interface**

In `Card.java`, before the closing brace, add:

```java
    /** Short stable id used in telemetry (e.g. "IntroCard"). */
    default String analyticsId() {
        return getClass().getSimpleName();
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/ui/cards/WrappedContext.java src/main/java/fr/zeffut/mcwrapped/ui/cards/Card.java
git commit -m "feat(telemetry): add WrappedContext distance/context helpers + Card.analyticsId"
```

---

## Task 10: WrappedLauncher chokepoint (trigger / started / generated)

**Files:**
- Create: `src/main/java/fr/zeffut/mcwrapped/ui/WrappedLauncher.java`
- Modify: `src/main/java/fr/zeffut/mcwrapped/command/WrappedCommand.java`
- Modify: `src/main/java/fr/zeffut/mcwrapped/ui/WrappedTitleButton.java`

- [ ] **Step 1: Create `WrappedLauncher`**

Create `src/main/java/fr/zeffut/mcwrapped/ui/WrappedLauncher.java`:

```java
package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.archetype.Archetype;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.StatsBucketer;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import fr.zeffut.mcwrapped.ui.cards.Card;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import fr.zeffut.mcwrapped.ui.cards.WrappedSequence;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single entry point to open a Wrapped experience. Centralizes the telemetry fired when a wrapped
 * is launched so every caller (command, title button, ...) records consistent events.
 */
public final class WrappedLauncher {

    private WrappedLauncher() {}

    /**
     * Opens the wrapped card screen for {@code ctx}, emitting trigger / started / generated events.
     *
     * @param source where the launch came from: "command", "title_button", ...
     */
    public static void open(@Nullable final Screen parent, final WrappedContext ctx, final String source) {
        Telemetry.capture(Events.WRAPPED_TRIGGERED, Map.of("source", source));

        final List<Card> cards = WrappedSequence.full(ctx);

        Telemetry.capture(Events.WRAPPED_STARTED, Map.of(
                "month", ctx.month().toString(),
                "card_count", cards.size()));

        final Map<String, Object> gen = new HashMap<>();
        gen.put("archetype", Archetype.pick(ctx).name());
        gen.put("playtime_bucket", StatsBucketer.playtimeBucket(ctx.playTimeTicks()));
        gen.put("deaths_bucket", StatsBucketer.deathsBucket(ctx.deaths()));
        gen.put("distance_bucket", StatsBucketer.distanceBucket(ctx.totalDistanceCm()));
        gen.put("context_type", ctx.contextType());
        gen.put("cards_shown", cards.size());
        Telemetry.capture(Events.WRAPPED_GENERATED, gen);

        MinecraftClient.getInstance().setScreen(new WrappedCardScreen(parent, cards));
    }
}
```

- [ ] **Step 2: Route `WrappedCommand.openLatest` through the launcher**

In `WrappedCommand.java`, replace the body of `openLatest` from the `final WrappedContext ctx ...` line to its end:

```java
        final WrappedContext ctx = new WrappedContext(target.month(), target.delta());
        client.send(() -> client.setScreen(new WrappedCardScreen(client.currentScreen, WrappedSequence.full(ctx))));
        return Command.SINGLE_SUCCESS;
```

with:

```java
        final WrappedContext ctx = new WrappedContext(target.month(), target.delta());
        client.send(() -> fr.zeffut.mcwrapped.ui.WrappedLauncher.open(client.currentScreen, ctx, "command"));
        return Command.SINGLE_SUCCESS;
```

(The `WrappedCardScreen` / `WrappedSequence` imports may become unused in this file — remove them if the compiler warns and they are no longer referenced.)

- [ ] **Step 3: Route `WrappedTitleButton.openWrapped` through the launcher**

In `WrappedTitleButton.java`, replace the body of `openWrapped`:

```java
        snapshots.saveWrapped(wrapped.asConsumed());
        final WrappedContext context = new WrappedContext(wrapped.month(), wrapped.delta());
        MinecraftClient.getInstance().setScreen(new WrappedCardScreen(parent, WrappedSequence.full(context)));
```

with:

```java
        snapshots.saveWrapped(wrapped.asConsumed());
        final WrappedContext context = new WrappedContext(wrapped.month(), wrapped.delta());
        WrappedLauncher.open(parent, context, "title_button");
```

Add the import `import fr.zeffut.mcwrapped.ui.WrappedLauncher;` (same package — if `WrappedTitleButton` is already in `fr.zeffut.mcwrapped.ui`, no import needed). Remove the now-unused `WrappedSequence` import if the compiler warns.

- [ ] **Step 4: Emit `wrapped_ready_shown` when the title button appears**

In `WrappedTitleButton.register(...)`, immediately after `Screens.getButtons(screen).add(button);`, add:

```java
            fr.zeffut.mcwrapped.telemetry.Telemetry.capture(
                    fr.zeffut.mcwrapped.telemetry.Events.WRAPPED_READY_SHOWN,
                    java.util.Map.of("month", wrapped.month().toString()));
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/ui/WrappedLauncher.java src/main/java/fr/zeffut/mcwrapped/command/WrappedCommand.java src/main/java/fr/zeffut/mcwrapped/ui/WrappedTitleButton.java
git commit -m "feat(telemetry): WrappedLauncher chokepoint for trigger/started/generated + ready_shown"
```

---

## Task 11: WrappedCardScreen instrumentation (card_viewed / completed / skipped / fps)

**Files:**
- Modify: `src/main/java/fr/zeffut/mcwrapped/ui/WrappedCardScreen.java`

The screen tracks per-card wall-clock duration, samples FPS each tick, and emits events when leaving a card and when the run ends (naturally → completed, via ESC → skipped).

- [ ] **Step 1: Add imports and tracking fields**

In `WrappedCardScreen.java`, add imports:

```java
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import java.util.HashMap;
import java.util.Map;
```

Add fields after `private float speedAccumulator = 0f;`:

```java
    private long cardStartMillis = 0L;
    private long fpsSampleSum = 0L;
    private long fpsSampleCount = 0L;
    private int fpsMin = Integer.MAX_VALUE;
    private boolean ended = false;
```

- [ ] **Step 2: Record card start time**

Add a helper and call it where cards start. Add this method:

```java
    private void markCardStart() {
        cardStartMillis = System.currentTimeMillis();
    }
```

In `init()`, inside the `if (!currentStarted && !cards.isEmpty())` block, after the `cards.get(currentIndex).start(...)` line, add:

```java
            markCardStart();
```

In `advanceCard(...)`, after `cards.get(currentIndex).start(vw, vh);` (inside the `if`), add:

```java
            markCardStart();
```

In `jumpToCard(...)`, after `cards.get(currentIndex).start(...)`, add:

```java
        markCardStart();
```

- [ ] **Step 3: Emit `card_viewed` when leaving a card**

Add this helper:

```java
    private void emitCardViewed(final int index) {
        if (index < 0 || index >= cards.size()) return;
        final long durationMs = System.currentTimeMillis() - cardStartMillis;
        Telemetry.capture(Events.CARD_VIEWED, Map.of(
                "card_id", cards.get(index).analyticsId(),
                "index", index,
                "duration_ms", durationMs));
    }
```

Call it before each transition away from the current card:

- In `tickOnce()`, in the `current.isDone()` branch, change:

```java
        if (current.isDone()) {
            if (currentIndex >= cards.size() - 1) close();
            else transitionTicks = 0;
        }
```

to:

```java
        if (current.isDone()) {
            emitCardViewed(currentIndex);
            if (currentIndex >= cards.size() - 1) {
                endRun(false);
                close();
            } else {
                transitionTicks = 0;
            }
        }
```

- In `jumpToCard(...)`, at the very start of the method (after the bounds guard `if (targetIndex < 0 || targetIndex >= cards.size()) return;`), add:

```java
        emitCardViewed(currentIndex);
```

- [ ] **Step 4: Sample FPS each tick**

In `tickOnce()`, at the very top (before `final int vw = ...`), add:

```java
        final int fps = MinecraftClient.getInstance().getCurrentFps();
        if (fps > 0) {
            fpsSampleSum += fps;
            fpsSampleCount++;
            if (fps < fpsMin) fpsMin = fps;
        }
```

- [ ] **Step 5: Emit completed / skipped + fps on run end**

Add this helper:

```java
    /** Fires once when the run ends. {@code skipped} distinguishes ESC from natural completion. */
    private void endRun(final boolean skipped) {
        if (ended) return;
        ended = true;

        if (skipped) {
            Telemetry.capture(Events.WRAPPED_SKIPPED, Map.of(
                    "card_id", cards.isEmpty() ? "none" : cards.get(currentIndex).analyticsId(),
                    "index", currentIndex));
        } else {
            Telemetry.capture(Events.WRAPPED_COMPLETED, Map.of(
                    "cards_viewed", cards.size(),
                    "completion_pct", 100));
        }

        if (fpsSampleCount > 0) {
            final Map<String, Object> fpsProps = new HashMap<>();
            fpsProps.put("avg_fps", (int) (fpsSampleSum / fpsSampleCount));
            fpsProps.put("min_fps", fpsMin == Integer.MAX_VALUE ? 0 : fpsMin);
            Telemetry.capture(Events.ANIMATION_FPS, fpsProps);
        }
    }
```

In `keyPressed(...)`, in the `GLFW.GLFW_KEY_ESCAPE` branch, before `close();`, add:

```java
            emitCardViewed(currentIndex);
            endRun(true);
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/ui/WrappedCardScreen.java
git commit -m "feat(telemetry): card_viewed/completed/skipped + animation fps"
```

---

## Task 12: FinalCard engagement (image_saved / clipboard_copied / export_failed)

**Files:**
- Modify: `src/main/java/fr/zeffut/mcwrapped/ui/cards/FinalCard.java`

- [ ] **Step 1: Add imports**

In `FinalCard.java`, add:

```java
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import java.util.Map;
```

(If `ConfigManager` is already imported, skip that line.)

- [ ] **Step 2: Instrument `triggerCopy()`**

In `triggerCopy()`, in the success branch (inside `MinecraftClient.getInstance().execute(...)` after `toastMessage = "Copied to clipboard!";`), add:

```java
                    Telemetry.capture(Events.CLIPBOARD_COPIED, Map.of("success", true));
```

In the `catch` block, after `McWrappedClient.LOGGER.warn("Clipboard copy failed", e);`, add:

```java
                Telemetry.capture(Events.CLIPBOARD_COPIED, Map.of("success", false));
```

- [ ] **Step 3: Instrument `triggerSave()`**

In `triggerSave()`, in the success branch (after `toastMessage = "Saved to screenshots/wrapped/" + file.getFileName();`), add:

```java
                    Telemetry.capture(Events.IMAGE_SAVED, Map.of(
                            "success", true,
                            "aspect_ratio", ConfigManager.get().aspectRatio.name()));
```

In the `catch` block, after `McWrappedClient.LOGGER.warn("Image export failed", e);`, add:

```java
                Telemetry.capture(Events.IMAGE_SAVED, Map.of(
                        "success", false,
                        "aspect_ratio", ConfigManager.get().aspectRatio.name()));
                Telemetry.capture(Events.EXPORT_FAILED, Map.of(
                        "stage", "save",
                        "reason", e.getClass().getSimpleName()));
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/ui/cards/FinalCard.java
git commit -m "feat(telemetry): FinalCard image_saved/clipboard_copied/export_failed"
```

---

## Task 13: Command engagement + telemetry subcommand

**Files:**
- Modify: `src/main/java/fr/zeffut/mcwrapped/command/WrappedCommand.java`

- [ ] **Step 1: Add imports**

In `WrappedCommand.java`, add:

```java
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import java.util.Map;
```

- [ ] **Step 2: Emit `command_used` + `history_opened` + `config_opened`**

In `register(...)`, in the `history` literal's `executes` lambda, before `client.send(...)`, add:

```java
                        Telemetry.capture(Events.COMMAND_USED, Map.of("command", "history"));
                        Telemetry.capture(Events.HISTORY_OPENED, Map.of("entry_count", snapshots.listWrapped().size()));
```

In the `config` literal's `executes` lambda, before `client.send(...)`, add:

```java
                        Telemetry.capture(Events.COMMAND_USED, Map.of("command", "config"));
```

In `openLatest(...)`, at the very start of the method (first line), add:

```java
        Telemetry.capture(Events.COMMAND_USED, Map.of("command", "wrapped"));
```

- [ ] **Step 3: Add the `telemetry` subcommand**

In `register(...)`, chain a new subcommand onto the `wrapped` literal (after the `config` `.then(...)` block, before the closing `)` of `dispatcher.register`):

```java
                    .then(ClientCommandManager.literal("telemetry")
                            .then(ClientCommandManager.literal("on").executes(c -> {
                                setTelemetry(true);
                                return Command.SINGLE_SUCCESS;
                            }))
                            .then(ClientCommandManager.literal("off").executes(c -> {
                                setTelemetry(false);
                                return Command.SINGLE_SUCCESS;
                            }))
                            .then(ClientCommandManager.literal("status").executes(c -> {
                                final boolean on = ConfigManager.get().telemetryEnabled;
                                feedback("Telemetry is " + (on ? "ON" : "OFF") + ".");
                                return Command.SINGLE_SUCCESS;
                            })))
```

- [ ] **Step 4: Add the `setTelemetry` and `feedback` helpers**

Add to `WrappedCommand`:

```java
    private static void setTelemetry(final boolean enable) {
        final boolean was = ConfigManager.get().telemetryEnabled;
        if (enable && !was) {
            ConfigManager.get().telemetryEnabled = true;
            ConfigManager.save();
            Telemetry.capture(Events.TELEMETRY_OPT_IN, Map.of());
            feedback("Telemetry enabled. Thanks for helping improve the mod!");
        } else if (!enable && was) {
            // Record the opt-out BEFORE disabling, then flip the flag.
            Telemetry.captureIgnoringConsent(Events.TELEMETRY_OPT_OUT, Map.of());
            ConfigManager.get().telemetryEnabled = false;
            ConfigManager.save();
            feedback("Telemetry disabled. Nothing more will be sent.");
        } else {
            feedback("Telemetry already " + (enable ? "ON" : "OFF") + ".");
        }
    }

    private static void feedback(final String message) {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/command/WrappedCommand.java
git commit -m "feat(telemetry): command_used/history_opened + /wrapped telemetry on|off|status"
```

---

## Task 14: Config screen — opened/changed events + telemetry toggle

**Files:**
- Modify: `src/main/java/fr/zeffut/mcwrapped/config/ui/McWrappedConfigScreen.java`

First read the file to confirm exact line numbers; the edits below describe insertion points by anchor code.

- [ ] **Step 1: Add imports**

In `McWrappedConfigScreen.java`, add:

```java
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import java.util.Map;
```

- [ ] **Step 2: Emit `config_opened` once when the screen initializes**

Add a field near the other instance fields:

```java
    private boolean openTracked = false;
```

In the `init()` / `protected void init()` method (the Screen override that builds widgets — find where the screen first lays out, typically `init()`), add at the top:

```java
        if (!openTracked) {
            Telemetry.capture(Events.CONFIG_OPENED, Map.of());
            openTracked = true;
        }
```

- [ ] **Step 3: Add a `recordChange` helper**

Add to `McWrappedConfigScreen`:

```java
    private static void recordChange(final String setting, final Object oldValue, final Object newValue) {
        Telemetry.capture(Events.CONFIG_CHANGED, Map.of(
                "setting", setting,
                "old_value", String.valueOf(oldValue),
                "new_value", String.valueOf(newValue)));
    }
```

- [ ] **Step 4: Add the telemetry toggle row**

Find the Privacy section where the `maskIgn` boolean button is built (the lines using `booleanButton(cfg.maskIgn, ...)`). Add an adjacent telemetry toggle row, modeled on that one. After the `maskIgn` row's `addDrawableChild(...)` call, add (adjusting `xField`, `y`, `fieldW`, `rowH`, and `y` advancement to match the file's layout pattern — copy the exact pattern used by the surrounding rows):

```java
        // Telemetry opt-out toggle.
        addLabel("Telemetry", x, y);
        addDrawableChild(
                booleanButton(cfg.telemetryEnabled, xField, y, fieldW, rowH,
                        v -> {
                            final boolean was = cfg.telemetryEnabled;
                            if (!v && was) {
                                Telemetry.captureIgnoringConsent(Events.TELEMETRY_OPT_OUT, Map.of());
                            }
                            cfg.telemetryEnabled = v;
                            ConfigManager.save();
                            if (v && !was) Telemetry.capture(Events.TELEMETRY_OPT_IN, Map.of());
                            recordChange("telemetryEnabled", was, v);
                            rebuild();
                        }));
        y += rowH + rowGap;
```

> NOTE: match the exact label-drawing call and `y`-advancement idiom already used in this file
> (the snippet above assumes an `addLabel(text, x, y)` helper and `rowGap` — if the file uses
> different names, mirror whatever the `maskIgn` / `autoTriggerEnabled` rows do).

- [ ] **Step 5: Instrument the existing change lambdas with `recordChange`**

For each existing setting handler in this file, add a `recordChange(...)` call. Add these one-liners inside the respective lambdas (each right after the field is mutated):

- `maskIgn` handler: `recordChange("maskIgn", !v, v);`
- `autoTriggerEnabled` handler: `recordChange("autoTriggerEnabled", !v, v);`
- theme cycle (`cycleTheme`): at the end of `cycleTheme`, `recordChange("theme", "(cycled)", cfg.theme.name());`
- gradient button handler: `recordChange("gradient", "(cycled)", cfg.gradient.name());`
- sparkleDensity button handler: `recordChange("sparkleDensity", "(cycled)", cfg.sparkleDensity.name());`
- transition button handler: `recordChange("transition", "(cycled)", cfg.transition.name());`
- aspectRatio button handler: `recordChange("aspectRatio", "(cycled)", cfg.aspectRatio.name());`

(If a handler does not have both old and new values handy, pass `"(changed)"` for the old value. The goal is to know WHICH setting changed, not to perfectly diff every value.)

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/fr/zeffut/mcwrapped/config/ui/McWrappedConfigScreen.java
git commit -m "feat(telemetry): config_opened/config_changed + telemetry toggle in config screen"
```

---

## Task 15: Full build, in-game verification, docs

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Full build + tests**

Run: `./gradlew clean build`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 2: In-game smoke (manual)**

Run: `./gradlew runClient`

In game:
1. Confirm the game boots normally (telemetry init must not block startup).
2. Run `/wrapped` (or use a test wrapped) and watch the cards; press ESC mid-way once, complete another run.
3. Click Save and Copy on the final card.
4. Open the config screen, toggle a setting and the Telemetry toggle off then on.
5. Run `/wrapped telemetry status`.

In PostHog → project 192659 → Activity / Live events, filter by `app = minecraft-wrapped`. Confirm `mcw_mod_loaded`, `mcw_wrapped_triggered`, `mcw_card_viewed`, `mcw_wrapped_completed`/`mcw_wrapped_skipped`, `mcw_image_saved`, `mcw_config_changed`, `mcw_telemetry_opt_out` arrive. (Events may take a few seconds to flush.)

If nothing arrives: verify the host (EU vs US) constant in `PostHogTelemetrySink`, then rebuild.

- [ ] **Step 3: Add a privacy section to README**

In `README.md`, add a section:

```markdown
## Telemetry & Privacy

Minecraft Wrapped sends anonymous usage telemetry to help improve the mod (which cards
people watch, which features get used, errors). It is **enabled by default** and you can
turn it off at any time:

- In-game: `/wrapped telemetry off`
- Config screen (ModMenu → Minecraft Wrapped): **Telemetry** toggle

**What is sent:** mod/Minecraft/Fabric versions, OS, language, which cards you view and for
how long, feature usage (export/save/copy/commands/config changes), coarse gameplay buckets
(e.g. playtime "10-50h", not exact numbers), your archetype, and mod errors.

**What is never sent:** your username, world names, server addresses, file paths, chat, or
exact stat values. IP addresses are anonymized. Telemetry is processed via PostHog.
```

- [ ] **Step 4: Update CLAUDE.md decision**

In `CLAUDE.md`, in section 8 ("Choses à NE PAS faire"), replace the two lines:

```markdown
- ❌ **Pas de monétisation, pas d'analytics, pas de tracking**. Privacy first.
```
```markdown
- ❌ **Pas de network calls** dans le MVP. Le mod marche 100% offline.
```

with:

```markdown
- ✅ **Télémétrie opt-out anonyme via PostHog** (depuis v1.2). Activée par défaut, désactivable
  via `/wrapped telemetry off` ou le toggle de config. Aucune donnée perso, IP anonymisée.
  Voir `docs/superpowers/specs/2026-06-03-posthog-telemetry-design.md`.
- ❌ **Pas de monétisation, pas de monétisation forcée.** Privacy-first reste la règle pour tout
  le reste : pas de PII, pas de tracking publicitaire.
```

- [ ] **Step 5: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: telemetry privacy notice + update project decisions"
```

---

## Self-review notes (coverage vs spec)

- **Consent (opt-out):** Task 7 (field, default true) + Task 13 (command) + Task 14 (toggle). `telemetry_opt_in`/`telemetry_opt_out` fire from both surfaces. ✅
- **Identity (raw UUID):** Task 8 `initTelemetry`. ✅
- **Super-props incl. `app` tag + `mcw_` prefix:** Task 3 (`EventContext`) + Task 4 (`Events`), merged in Task 5 (`Telemetry`). ✅
- **Lifecycle events:** `mod_loaded` (T8), `wrapped_triggered`/`wrapped_started`/`wrapped_generated` (T10), `card_viewed`/`wrapped_completed`/`wrapped_skipped` (T11), `wrapped_ready_shown` (T10). ✅
- **Engagement:** `image_saved`/`clipboard_copied`/`export_failed` (T12), `command_used`/`history_opened` (T13), `config_opened`/`config_changed` (T14). ✅
- **Gameplay aggregated:** `wrapped_generated` with archetype + buckets + context_type (T10). ✅
- **Errors & perf:** `export_failed` (T12), `wrapped_generation_time` (T8), `animation_fps` (T11). ✅
- **Bundling (shadow + relocation):** Task 1. ✅
- **Docs update:** Task 15. ✅

**Known deferrals (explicitly out, not silent):**
- `wrapped_ready_dismissed` — no clean hook to detect a title-screen dismissal without a click; only `wrapped_ready_shown` is implemented. Revisit if dismissal rate becomes important.
- `error_caught` / `json_parse_failed` generic wrappers — the spec lists these as a category; this plan instruments the concrete error paths that exist today (`export_failed`). Add `error_caught` calls opportunistically in future `catch` blocks rather than introducing a broad handler now (YAGNI).
- `pause_button_clicked` — depends on the pause-menu Wrapped button (mentioned in the brief but not confirmed present in current code); wire it the same way as `command_used` if/when that button exists.
