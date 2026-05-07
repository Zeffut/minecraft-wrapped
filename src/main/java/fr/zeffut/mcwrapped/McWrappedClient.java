package fr.zeffut.mcwrapped;

import fr.zeffut.mcwrapped.stats.MonthlyDelta;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.StatsContext;
import fr.zeffut.mcwrapped.stats.StatsReader;
import fr.zeffut.mcwrapped.stats.StatsSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> captureAndReport(client));
        });
    }

    private void captureAndReport(final MinecraftClient client) {
        final Optional<StatsContext> ctxOpt = StatsContext.current(client);
        if (ctxOpt.isEmpty()) {
            LOGGER.debug("No stats context available, skipping.");
            return;
        }
        final StatsContext ctx = ctxOpt.get();

        if (ctx.kind() != StatsContext.Kind.SINGLEPLAYER) {
            LOGGER.info("Stats capture for servers will land in S5. Context: {}", ctx.id());
            return;
        }

        final Optional<Map<String, Map<String, Long>>> rawOpt = StatsReader.readSingleplayer(client);
        if (rawOpt.isEmpty()) {
            LOGGER.info("No stats data yet for {}.", ctx.id());
            return;
        }

        final YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
        final StatsSnapshot current = new StatsSnapshot(currentMonth, ctx.id(), Instant.now(), rawOpt.get());

        final Optional<StatsSnapshot> latest = snapshots.loadLatest(ctx);
        snapshots.save(ctx, current);

        if (latest.isEmpty()) {
            LOGGER.info("First snapshot saved for {}. Reviens le mois prochain pour ton premier Wrapped !", ctx.id());
            return;
        }

        final StatsSnapshot prev = latest.get();
        if (prev.month().equals(currentMonth)) {
            LOGGER.info("Snapshot updated for current month {} ({}).", currentMonth, ctx.id());
            return;
        }

        final MonthlyDelta delta = MonthlyDelta.compute(prev, current);
        logDelta(prev.month(), delta);
    }

    private static void logDelta(final YearMonth month, final MonthlyDelta delta) {
        LOGGER.info("=== Wrapped recap for {} ===", month);
        LOGGER.info("Context: {}", delta.contextId());

        final Map<String, Long> custom = delta.deltas().getOrDefault("minecraft:custom", Map.of());
        final long playTicks = custom.getOrDefault("minecraft:play_time", 0L);
        LOGGER.info("Play time: {} min ({} ticks)", playTicks / 20 / 60, playTicks);
        LOGGER.info("Deaths: {}", custom.getOrDefault("minecraft:deaths", 0L));
        LOGGER.info("Mob kills: {}", custom.getOrDefault("minecraft:mob_kills", 0L));
        LOGGER.info("Jumps: {}", custom.getOrDefault("minecraft:jump", 0L));

        LOGGER.info("Blocks mined total: {}", delta.total("minecraft:mined"));
        LOGGER.info("Mobs killed total: {}", delta.total("minecraft:killed"));
        LOGGER.info("Items used total: {}", delta.total("minecraft:used"));
        LOGGER.info("Items crafted total: {}", delta.total("minecraft:crafted"));

        delta.deltas().forEach((category, values) -> {
            LOGGER.debug("[{}] {} entries", category, values.size());
            values.forEach((key, value) -> LOGGER.debug("  {} = {}", key, value));
        });
        LOGGER.info("=== end Wrapped recap ===");
    }
}
