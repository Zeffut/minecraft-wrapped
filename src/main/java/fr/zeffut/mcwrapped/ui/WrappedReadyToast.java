package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * Pops a vanilla toast when an unconsumed Wrapped exists, on title screen and on server join.
 * Shows once per session to avoid spamming.
 */
public final class WrappedReadyToast {

    private static boolean shownThisSession = false;

    private WrappedReadyToast() {}

    public static void register(final SnapshotManager snapshots) {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) tryShow(client, snapshots);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> tryShow(client, snapshots));
    }

    private static void tryShow(final Minecraft client, final SnapshotManager snapshots) {
        if (shownThisSession || client == null) return;
        final fr.zeffut.mcwrapped.config.McWrappedConfig cfg = fr.zeffut.mcwrapped.config.ConfigManager.get();
        if (!cfg.autoTriggerEnabled) return;
        final int today = java.time.LocalDate.now().getDayOfMonth();
        if (today > Math.max(1, Math.min(31, cfg.autoTriggerGraceDays))) return;
        final Optional<WrappedFile> ready = snapshots.findLatestUnconsumed();
        if (ready.isEmpty()) return;
        shownThisSession = true;

        final WrappedFile wf = ready.get();
        client.getToasts().add(new SystemToast(
                SystemToast.Type.PERIODIC_NOTIFICATION,
                Component.literal("Minecraft Wrapped"),
                Component.literal("Your " + monthLabel(wf.month()) + " is ready")
        ));
    }

    private static String monthLabel(final YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
    }
}
