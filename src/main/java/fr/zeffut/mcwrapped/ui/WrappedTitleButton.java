package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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
            final Optional<WrappedFile> ready = snapshots.findLatestUnconsumed();
            if (ready.isEmpty()) return;

            final WrappedFile wrapped = ready.get();
            // Top-right — clear of the bottom Mojang/version/copyright line.
            final int x = scaledWidth - BUTTON_WIDTH - MARGIN;
            final int y = MARGIN;
            final ButtonWidget button = ButtonWidget.builder(
                            Text.translatable("mcwrapped.button.ready", monthLabel(wrapped.month())),
                            btn -> openWrapped(snapshots, screen, wrapped))
                    .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            Screens.getButtons(screen).add(button);
        });
    }

    private static void openWrapped(final SnapshotManager snapshots, final net.minecraft.client.gui.screen.Screen parent, final WrappedFile wrapped) {
        snapshots.saveWrapped(wrapped.asConsumed());
        MinecraftClient.getInstance().setScreen(new WrappedScreen(parent, wrapped));
    }

    private static String monthLabel(final YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }
}
