package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import fr.zeffut.mcwrapped.ui.cards.WrappedSequence;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

public final class WrappedTitleButton {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MARGIN = 4;

    private WrappedTitleButton() {}

    public static void register(final SnapshotManager snapshots) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) return;
            final fr.zeffut.mcwrapped.config.McWrappedConfig cfg = fr.zeffut.mcwrapped.config.ConfigManager.get();
            if (!cfg.autoTriggerEnabled) return;
            if (!withinGraceWindow(cfg.autoTriggerGraceDays)) return;
            final Optional<WrappedFile> ready = snapshots.findLatestUnconsumed();
            if (ready.isEmpty()) return;

            final WrappedFile wrapped = ready.get();
            // Top-right — clear of the bottom Mojang/version/copyright line.
            final int x = scaledWidth - BUTTON_WIDTH - MARGIN;
            final int y = MARGIN;
            final Button button = Button.builder(
                            Component.translatable("mcwrapped.button.ready", monthLabel(wrapped.month())),
                            btn -> openWrapped(snapshots, screen, wrapped))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            Screens.getButtons(screen).add(button);
        });
    }

    private static void openWrapped(final SnapshotManager snapshots, final net.minecraft.client.gui.screens.Screen parent, final WrappedFile wrapped) {
        snapshots.saveWrapped(wrapped.asConsumed());
        final WrappedContext context = new WrappedContext(wrapped.month(), wrapped.delta());
        Minecraft.getInstance().setScreen(new WrappedCardScreen(parent, WrappedSequence.full(context)));
    }

    private static String monthLabel(final YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    /** True if today's day-of-month is within the configured grace window (1..N). */
    private static boolean withinGraceWindow(final int graceDays) {
        final int today = java.time.LocalDate.now().getDayOfMonth();
        return today <= Math.max(1, Math.min(31, graceDays));
    }
}
