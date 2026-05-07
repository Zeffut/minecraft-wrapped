package fr.zeffut.mcwrapped;

import fr.zeffut.mcwrapped.stats.MonthlyDelta;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.StatsReader;
import fr.zeffut.mcwrapped.stats.StatsSnapshot;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.ui.WrappedTitleButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

public final class McWrappedClient implements ClientModInitializer {
    public static final String MOD_ID = "mcwrapped";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final SnapshotManager snapshots = new SnapshotManager();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Minecraft Wrapped initialized.");

        ClientLifecycleEvents.CLIENT_STARTED.register(this::captureAndFinalize);
        WrappedTitleButton.register(snapshots);
    }

    /**
     * On every game start: refresh the cumulative-stats snapshot for the current month.
     * If the previous snapshot belongs to an earlier month, finalize a wrapped file for it.
     */
    private void captureAndFinalize(final MinecraftClient client) {
        final Optional<Map<String, Map<String, Long>>> rawOpt = StatsReader.readAggregated(client);
        if (rawOpt.isEmpty() || rawOpt.get().isEmpty()) {
            LOGGER.info("No stats data found yet — come back next month for your first Wrapped!");
            return;
        }

        final YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
        final StatsSnapshot current = new StatsSnapshot(currentMonth, Instant.now(), rawOpt.get());
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

        final MonthlyDelta delta = MonthlyDelta.compute(prev, current);
        snapshots.saveWrapped(new WrappedFile(prev.month(), delta, false));
        LOGGER.info("Wrapped ready for {} — a button will appear on the title screen.", prev.month());
    }
}
