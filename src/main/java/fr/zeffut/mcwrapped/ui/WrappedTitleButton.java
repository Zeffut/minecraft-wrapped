package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.McWrappedClient;
import fr.zeffut.mcwrapped.stats.MonthlyDelta;
import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WrappedTitleButton {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private WrappedTitleButton() {}

    public static void register(final SnapshotManager snapshots) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) return;
            final Optional<WrappedFile> ready = snapshots.findLatestUnconsumed();
            if (ready.isEmpty()) return;

            final WrappedFile wrapped = ready.get();
            final ButtonWidget button = ButtonWidget.builder(
                            Text.translatable("mcwrapped.button.ready", monthLabel(wrapped.month())),
                            btn -> openWrapped(snapshots, wrapped))
                    .dimensions(scaledWidth - BUTTON_WIDTH - 4, scaledHeight - BUTTON_HEIGHT - 4, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            Screens.getButtons(screen).add(button);
        });
    }

    private static void openWrapped(final SnapshotManager snapshots, final WrappedFile wrapped) {
        // S1: log only. The animated experience lands in S2-S4.
        logRecap(wrapped.month(), wrapped.delta());
        snapshots.saveWrapped(wrapped.asConsumed());
    }

    private static String monthLabel(final YearMonth month) {
        final String name = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return name + " " + month.getYear();
    }

    private static void logRecap(final YearMonth month, final MonthlyDelta delta) {
        McWrappedClient.LOGGER.info("=== Wrapped recap for {} ===", month.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        final Map<String, Long> custom = delta.deltas().getOrDefault("minecraft:custom", Map.of());
        final long playTicks = custom.getOrDefault("minecraft:play_time", 0L);
        McWrappedClient.LOGGER.info("Play time: {} min ({} ticks)", playTicks / 20 / 60, playTicks);
        McWrappedClient.LOGGER.info("Deaths: {}", custom.getOrDefault("minecraft:deaths", 0L));
        McWrappedClient.LOGGER.info("Mob kills: {}", custom.getOrDefault("minecraft:mob_kills", 0L));
        McWrappedClient.LOGGER.info("Jumps: {}", custom.getOrDefault("minecraft:jump", 0L));
        McWrappedClient.LOGGER.info("Blocks mined total: {}", delta.total("minecraft:mined"));
        McWrappedClient.LOGGER.info("Mobs killed total: {}", delta.total("minecraft:killed"));
        McWrappedClient.LOGGER.info("Items used total: {}", delta.total("minecraft:used"));
        McWrappedClient.LOGGER.info("Items crafted total: {}", delta.total("minecraft:crafted"));
        McWrappedClient.LOGGER.info("=== end Wrapped recap ===");
    }
}
