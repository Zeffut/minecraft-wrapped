package fr.zeffut.mcwrapped.command;

import com.mojang.brigadier.Command;
import fr.zeffut.mcwrapped.stats.MonthlyDelta;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WorldKey;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.ui.WrappedCardScreen;
import fr.zeffut.mcwrapped.ui.cards.Card;
import fr.zeffut.mcwrapped.ui.cards.ArchetypeCard;
import fr.zeffut.mcwrapped.ui.cards.DeathRecapCard;
import fr.zeffut.mcwrapped.ui.cards.DistanceCard;
import fr.zeffut.mcwrapped.ui.cards.FinalCard;
import fr.zeffut.mcwrapped.ui.cards.IntroCard;
import fr.zeffut.mcwrapped.ui.cards.SocialCard;
import fr.zeffut.mcwrapped.ui.cards.TimeSpentCard;
import fr.zeffut.mcwrapped.ui.cards.TopBlocksCard;
import fr.zeffut.mcwrapped.ui.cards.TopMobCard;
import fr.zeffut.mcwrapped.ui.cards.TopWorldCard;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import fr.zeffut.mcwrapped.ui.cards.WrappedSequence;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class WrappedCommand {

    private WrappedCommand() {}

    public static void register(final SnapshotManager snapshots) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("wrapped")
                    .then(ClientCommandManager.literal("test")
                            .then(ClientCommandManager.literal("intro").executes(c -> open(snapshots, "intro", ctx -> List.of(new IntroCard(ctx.month())))))
                            .then(ClientCommandManager.literal("time").executes(c -> open(snapshots, "time", ctx -> List.of(new TimeSpentCard(ctx)))))
                            .then(ClientCommandManager.literal("blocks").executes(c -> open(snapshots, "blocks", ctx -> List.of(new TopBlocksCard(ctx)))))
                            .then(ClientCommandManager.literal("mob").executes(c -> open(snapshots, "mob", ctx -> List.of(new TopMobCard(ctx)))))
                            .then(ClientCommandManager.literal("world").executes(c -> open(snapshots, "world", ctx -> List.of(new TopWorldCard(ctx)))))
                            .then(ClientCommandManager.literal("social").executes(c -> open(snapshots, "social", ctx -> List.of(new SocialCard(ctx)))))
                            .then(ClientCommandManager.literal("final").executes(c -> open(snapshots, "final", ctx -> List.of(new FinalCard(ctx)))))
                            .then(ClientCommandManager.literal("archetype").executes(c -> open(snapshots, "archetype", ctx -> List.of(new ArchetypeCard(ctx)))))
                            .then(ClientCommandManager.literal("distance").executes(c -> open(snapshots, "distance", ctx -> List.of(new DistanceCard(ctx)))))
                            .then(ClientCommandManager.literal("deaths").executes(c -> open(snapshots, "deaths", ctx -> List.of(new DeathRecapCard(ctx)))))
                            .then(ClientCommandManager.literal("full").executes(c -> open(snapshots, "full", WrappedSequence::full)))));
        });
    }

    private static int open(final SnapshotManager snapshots, final String label, final Function<WrappedContext, List<Card>> builder) {
        final WrappedContext ctx = loadContext(snapshots);
        final List<Card> cards = builder.apply(ctx);
        final MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(new WrappedCardScreen(client.currentScreen, cards)));
        return Command.SINGLE_SUCCESS;
    }

    private static WrappedContext loadContext(final SnapshotManager snapshots) {
        final List<WrappedFile> all = snapshots.listWrapped();
        if (!all.isEmpty()) {
            final WrappedFile latest = all.get(all.size() - 1);
            return new WrappedContext(latest.month(), latest.delta());
        }
        return mockContext();
    }

    private static WrappedContext mockContext() {
        final YearMonth month = YearMonth.now(ZoneId.systemDefault()).minusMonths(1);
        final Map<String, Map<String, Long>> deltas = new LinkedHashMap<>();

        final Map<String, Long> custom = new LinkedHashMap<>();
        custom.put("minecraft:play_time", (long) (47 * 60 * 60 * 20 + 23 * 60 * 20));
        custom.put("minecraft:deaths", 23L);
        custom.put("minecraft:mob_kills", 412L);
        custom.put("minecraft:jump", 8421L);
        custom.put("minecraft:walk_one_cm", 1_271_320L);
        deltas.put("minecraft:custom", custom);

        final Map<String, Long> mined = new LinkedHashMap<>();
        mined.put("minecraft:stone", 12384L);
        mined.put("minecraft:cobblestone", 4521L);
        mined.put("minecraft:dirt", 1842L);
        deltas.put("minecraft:mined", mined);

        final Map<String, Long> killed = new LinkedHashMap<>();
        killed.put("minecraft:zombie", 247L);
        killed.put("minecraft:skeleton", 102L);
        deltas.put("minecraft:killed", killed);

        final Map<String, Long> killedBy = new LinkedHashMap<>();
        killedBy.put("minecraft:chicken", 8L);
        killedBy.put("minecraft:zombie", 5L);
        deltas.put("minecraft:killed_by", killedBy);

        final Map<String, Long> perWorld = new LinkedHashMap<>();
        perWorld.put(WorldKey.singleplayer("Survival World"), (long) (18 * 60 * 60 * 20));
        perWorld.put(WorldKey.server("Hypixel"), (long) (16 * 60 * 60 * 20));
        perWorld.put(WorldKey.server("Friend's SMP"), (long) (8 * 60 * 60 * 20));
        perWorld.put(WorldKey.singleplayer("Hardcore Run"), (long) (5 * 60 * 60 * 20 + 23 * 60 * 20));

        final MonthlyDelta delta = new MonthlyDelta(month, deltas, perWorld, 47, 312, 18, 4);
        return new WrappedContext(month, delta);
    }
}
