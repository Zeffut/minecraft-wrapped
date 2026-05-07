package fr.zeffut.mcwrapped.command;

import com.mojang.brigadier.Command;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.ui.WrappedCardScreen;
import fr.zeffut.mcwrapped.ui.WrappedHistoryScreen;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import fr.zeffut.mcwrapped.ui.cards.WrappedSequence;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

public final class WrappedCommand {

    private WrappedCommand() {}

    public static void register(final SnapshotManager snapshots) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("wrapped")
                    .executes(c -> openLatest(snapshots))
                    .then(ClientCommandManager.literal("history").executes(c -> {
                        final MinecraftClient client = MinecraftClient.getInstance();
                        client.send(() -> client.setScreen(new WrappedHistoryScreen(client.currentScreen, snapshots)));
                        return Command.SINGLE_SUCCESS;
                    })));
        });
    }

    private static int openLatest(final SnapshotManager snapshots) {
        final List<WrappedFile> all = snapshots.listWrapped();
        if (all.isEmpty()) {
            final MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("No wrapped yet — come back next month."), false);
            }
            return Command.SINGLE_SUCCESS;
        }
        final WrappedFile latest = all.get(all.size() - 1);
        final WrappedContext ctx = new WrappedContext(latest.month(), latest.delta());
        final MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(new WrappedCardScreen(client.currentScreen, WrappedSequence.full(ctx))));
        return Command.SINGLE_SUCCESS;
    }
}
