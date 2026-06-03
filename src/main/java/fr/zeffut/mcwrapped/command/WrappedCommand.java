package fr.zeffut.mcwrapped.command;

import com.mojang.brigadier.Command;
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.config.ui.McWrappedConfigScreen;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import fr.zeffut.mcwrapped.ui.WrappedHistoryScreen;
import fr.zeffut.mcwrapped.ui.WrappedLauncher;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

public final class WrappedCommand {

    private WrappedCommand() {}

    public static void register(final SnapshotManager snapshots) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("wrapped")
                    .executes(c -> openLatest(snapshots))
                    .then(ClientCommandManager.literal("history").executes(c -> {
                        Telemetry.capture(Events.COMMAND_USED, Map.of("command", "history"));
                        Telemetry.capture(Events.HISTORY_OPENED, Map.of("entry_count", snapshots.listWrapped().size()));
                        final MinecraftClient client = MinecraftClient.getInstance();
                        client.send(() -> client.setScreen(new WrappedHistoryScreen(client.currentScreen, snapshots)));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(ClientCommandManager.literal("config").executes(c -> {
                        Telemetry.capture(Events.COMMAND_USED, Map.of("command", "config"));
                        final MinecraftClient client = MinecraftClient.getInstance();
                        client.send(() -> client.setScreen(new McWrappedConfigScreen(client.currentScreen)));
                        return Command.SINGLE_SUCCESS;
                    }))
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
                            }))));
        });
    }

    private static int openLatest(final SnapshotManager snapshots) {
        Telemetry.capture(Events.COMMAND_USED, Map.of("command", "wrapped"));
        final MinecraftClient client = MinecraftClient.getInstance();
        final WrappedFile target = pickTarget(snapshots);
        if (target == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("No wrapped to show — set a different target month in /wrapped config or wait for next month."), false);
            }
            return Command.SINGLE_SUCCESS;
        }
        final WrappedContext ctx = new WrappedContext(target.month(), target.delta());
        client.send(() -> WrappedLauncher.open(client.currentScreen, ctx, "command"));
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
}
