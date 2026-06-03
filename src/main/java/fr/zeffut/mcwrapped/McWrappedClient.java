package fr.zeffut.mcwrapped;

import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.stats.MonthlyDelta;
import fr.zeffut.mcwrapped.stats.DimensionTracker;
import fr.zeffut.mcwrapped.stats.MultiplayerTracker;
import fr.zeffut.mcwrapped.stats.ServerPlayTimeTracker;
import fr.zeffut.mcwrapped.stats.ServerStatsTracker;
import fr.zeffut.mcwrapped.stats.SessionTracker;
import fr.zeffut.mcwrapped.stats.TimeOfDayTracker;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.StatsReader;
import fr.zeffut.mcwrapped.stats.StatsSnapshot;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.command.WrappedCommand;
import fr.zeffut.mcwrapped.telemetry.EventContext;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.PostHogTelemetrySink;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import fr.zeffut.mcwrapped.ui.WrappedReadyToast;
import fr.zeffut.mcwrapped.ui.WrappedTitleButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class McWrappedClient implements ClientModInitializer {
    public static final String MOD_ID = "mcwrapped";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final SnapshotManager snapshots = new SnapshotManager();
    private final ServerPlayTimeTracker serverTracker = new ServerPlayTimeTracker();
    private final ServerStatsTracker serverStatsTracker = new ServerStatsTracker();
    private final MultiplayerTracker multiplayerTracker = new MultiplayerTracker();
    private final SessionTracker sessionTracker = new SessionTracker();
    private final TimeOfDayTracker timeOfDayTracker = new TimeOfDayTracker();
    private final DimensionTracker dimensionTracker = new DimensionTracker();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Minecraft Wrapped initialized.");
        // Eagerly load config so the file is created with defaults on first launch and every later
        // ConfigManager.get() call is just a field read.
        ConfigManager.init();
        initTelemetry();

        serverTracker.register();
        serverStatsTracker.register();
        multiplayerTracker.register();
        sessionTracker.register();
        timeOfDayTracker.register();
        dimensionTracker.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(this::captureAndFinalize);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Telemetry.shutdown());
        WrappedTitleButton.register(snapshots);
        WrappedReadyToast.register(snapshots);
        WrappedCommand.register(snapshots);
    }

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

    /**
     * On every game start: refresh the cumulative-stats snapshot for the current month.
     * If the previous snapshot belongs to an earlier month, finalize a wrapped file for it.
     */
    private void captureAndFinalize(final MinecraftClient client) {
        final Optional<StatsReader.Aggregated> aggOpt = StatsReader.readAggregated(client, serverTracker, serverStatsTracker);
        if (aggOpt.isEmpty() || aggOpt.get().total().isEmpty()) {
            LOGGER.info("No stats data found yet — come back next month for your first Wrapped!");
            return;
        }

        final YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
        final StatsSnapshot current = new StatsSnapshot(currentMonth, Instant.now(),
                aggOpt.get().total(), aggOpt.get().perWorldPlayTime(),
                multiplayerTracker.playersSeenCount(),
                multiplayerTracker.messagesSent(),
                multiplayerTracker.commandsSent(),
                multiplayerTracker.serversVisitedCount());
        final Optional<StatsSnapshot> latest = snapshots.loadLatestSnapshot();
        snapshots.saveSnapshot(current);

        if (latest.isEmpty()) {
            LOGGER.info("First snapshot saved. Come back next month for your first Wrapped!");
            return;
        }

        final StatsSnapshot prev = latest.get();
        if (!prev.month().isBefore(currentMonth)) {
            return;
        }

        if (snapshots.wrappedExists(prev.month())) {
            LOGGER.debug("Wrapped for {} already finalized.", prev.month());
            return;
        }

        final long t0 = System.nanoTime();
        final MonthlyDelta delta = MonthlyDelta.compute(prev, current);
        final long genMs = (System.nanoTime() - t0) / 1_000_000L;
        snapshots.saveWrapped(new WrappedFile(prev.month(), delta, false));
        Telemetry.capture(Events.WRAPPED_GENERATION_TIME, Map.of("duration_ms", genMs));
        LOGGER.info("Wrapped ready for {} — a button will appear on the title screen.", prev.month());
    }
}
