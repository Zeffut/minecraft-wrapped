package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.stats.SnapshotManager;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import fr.zeffut.mcwrapped.ui.cards.CardEffects;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import fr.zeffut.mcwrapped.ui.cards.WrappedSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Lists all finalized wrappeds and lets the player replay any of them.
 */
public final class WrappedHistoryScreen extends Screen {

    @Nullable
    private final Screen parent;
    private final SnapshotManager snapshots;
    private List<WrappedFile> entries = List.of();

    public WrappedHistoryScreen(@Nullable final Screen parent, final SnapshotManager snapshots) {
        super(Component.literal("Wrapped History"));
        this.parent = parent;
        this.snapshots = snapshots;
    }

    @Override
    protected void init() {
        entries = snapshots.listWrapped();

        final int rowH = 28;
        final int gap = 8;
        final int contentW = Math.min(420, width - 60);
        final int xLeft = width / 2 - contentW / 2;
        final int yTop = 70;

        for (int i = 0; i < entries.size(); i++) {
            final WrappedFile wf = entries.get(i);
            final int y = yTop + i * (rowH + gap);
            final long minutes = wf.delta().deltas().getOrDefault("minecraft:custom", java.util.Map.of())
                    .getOrDefault("minecraft:play_time", 0L) / 20 / 60;
            final String time = (minutes >= 60)
                    ? (minutes / 60) + "h " + String.format(Locale.ROOT, "%02d", minutes % 60) + "m"
                    : minutes + "m";
            final String label = monthLabel(wf.month()) + "  ·  " + time;
            addDrawableChild(Button.builder(Component.literal(label), btn -> replay(wf))
                    .bounds(xLeft, y, contentW, rowH - 4).build());
        }

        addDrawableChild(Button.builder(Component.literal("Back"), btn -> close())
                .bounds(width / 2 - 60, height - 30, 120, 20).build());
    }

    private void replay(final WrappedFile wf) {
        final WrappedContext ctx = new WrappedContext(wf.month(), wf.delta());
        client.setScreen(new WrappedCardScreen(this, WrappedSequence.full(ctx)));
    }

    @Override
    public void render(final GuiGraphics context, final int mouseX, final int mouseY, final float delta) {
        // Solid dark background to match the wrapped aesthetic.
        CardEffects.renderGradient(context, width, height, CardEffects.BG_TOP, CardEffects.BG_BOTTOM);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(font,
                Component.literal("WRAPPED HISTORY").formatted(ChatFormatting.GOLD), width / 2, 30, 0xFFFFFFFF);

        if (entries.isEmpty()) {
            context.drawCenteredString(font,
                    Component.literal("No wrapped to show yet — come back next month."),
                    width / 2, height / 2, 0xFFCBD5E1);
        }
    }

    @Override
    public void renderBackground(final GuiGraphics context, final int mouseX, final int mouseY, final float delta) {
        // We paint our own background.
    }

    @Override
    public void close() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(parent);
    }

    private static String monthLabel(final YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT)
                + " " + m.getYear();
    }
}
