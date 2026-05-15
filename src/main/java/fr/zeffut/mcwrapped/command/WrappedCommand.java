package fr.zeffut.mcwrapped.command;

import com.mojang.brigadier.Command;
import fr.zeffut.mcwrapped.config.ui.McWrappedConfigScreen;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.ui.WrappedCardScreen;
import fr.zeffut.mcwrapped.ui.WrappedHistoryScreen;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import fr.zeffut.mcwrapped.ui.cards.WrappedSequence;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class WrappedCommand {

    private WrappedCommand() {}

    public static void register(final SnapshotManager snapshots) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("wrapped")
                    .executes(c -> openLatest(snapshots))
                    .then(ClientCommandManager.literal("history").executes(c -> {
                        final Minecraft client = Minecraft.getInstance();
                        client.send(() -> client.setScreen(new WrappedHistoryScreen(client.currentScreen, snapshots)));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(ClientCommandManager.literal("config").executes(c -> {
                        final Minecraft client = Minecraft.getInstance();
                        client.send(() -> client.setScreen(new McWrappedConfigScreen(client.currentScreen)));
                        return Command.SINGLE_SUCCESS;
                    })));
        });
    }

    private static int openLatest(final SnapshotManager snapshots) {
        final Minecraft client = Minecraft.getInstance();
        final WrappedFile target = pickTarget(snapshots);
        if (target == null) {
            if (client.player != null) {
                client.player.sendMessage(Component.literal("No wrapped to show — set a different target month in /wrapped config or wait for next month."), false);
            }
            return Command.SINGLE_SUCCESS;
        }
        final WrappedContext ctx = new WrappedContext(target.month(), target.delta());
        client.send(() -> client.setScreen(new WrappedCardScreen(client.currentScreen, WrappedSequence.full(ctx))));
        return Command.SINGLE_SUCCESS;
    }

    /** Honors {@code config.targetMonth} when set, otherwise returns the most recent wrapped. */
    public static WrappedFile pickTarget(final SnapshotManager snapshots) {
        final String tm = fr.zeffut.mcwrapped.config.ConfigManager.get().targetMonth.trim();
        if (!tm.isEmpty()) {
            try {
                final java.time.YearMonth ym = java.time.YearMonth.parse(tm);
                return snapshots.loadWrapped(ym).orElse(null);
            } catch (final java.time.format.DateTimeParseException ignored) {
                // Fallthrough to "latest" if the user typed garbage.
            }
        }
        final List<WrappedFile> all = snapshots.listWrapped();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }
}
