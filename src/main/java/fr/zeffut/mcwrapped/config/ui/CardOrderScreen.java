package fr.zeffut.mcwrapped.config.ui;

import fr.zeffut.mcwrapped.config.CardId;
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.config.McWrappedConfig;
import fr.zeffut.mcwrapped.ui.cards.CardEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user toggle cards on/off and shuffle their order with ▲ / ▼ buttons.
 * Mutates {@link McWrappedConfig#cardOrder} and {@link McWrappedConfig#enabledCards} in place.
 */
public final class CardOrderScreen extends Screen {

    @Nullable private final Screen parent;
    private List<CardId> order;

    public CardOrderScreen(@Nullable final Screen parent) {
        super(Component.literal("Cards"));
        this.parent = parent;
        this.order = new ArrayList<>(ConfigManager.get().cardOrder);
    }

    @Override
    protected void init() {
        final McWrappedConfig cfg = ConfigManager.get();
        final int rowH = 22;
        final int gap = 4;
        final int contentW = Math.min(width - 40, 420);
        final int xLeft = width / 2 - contentW / 2;
        final int yTop = 50;
        final int maxRows = Math.max(0, (height - yTop - 60) / (rowH + gap));
        final int visibleRows = Math.min(order.size(), maxRows);

        for (int i = 0; i < visibleRows; i++) {
            final CardId id = order.get(i);
            final int y = yTop + i * (rowH + gap);
            final int rowIndex = i;

            // Toggle button (ON/OFF).
            final boolean enabled = cfg.isCardEnabled(id);
            addDrawableChild(Button.builder(
                    Component.literal(enabled ? "ON" : "OFF").formatted(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    btn -> {
                        cfg.enabledCards.put(id, !cfg.isCardEnabled(id));
                        ConfigManager.save();
                        rebuild();
                    }).bounds(xLeft, y, 40, rowH).build());

            // Up arrow.
            addDrawableChild(Button.builder(Component.literal("▲"), btn -> moveUp(rowIndex))
                    .bounds(xLeft + 50, y, 24, rowH).build());

            // Down arrow.
            addDrawableChild(Button.builder(Component.literal("▼"), btn -> moveDown(rowIndex))
                    .bounds(xLeft + 78, y, 24, rowH).build());

            // Card name (not a button — drawn in render()).
        }

        // Reset + Back at the bottom. The two action buttons share contentW so they always fit.
        final int actionW = (contentW - 10) / 2;
        addDrawableChild(Button.builder(Component.literal("Enable all"), btn -> {
            for (final CardId id : CardId.values()) cfg.enabledCards.put(id, true);
            ConfigManager.save();
            rebuild();
        }).bounds(xLeft, height - 50, actionW, 20).build());
        addDrawableChild(Button.builder(Component.literal("Default order"), btn -> {
            this.order = new ArrayList<>(List.of(CardId.values()));
            cfg.cardOrder = new ArrayList<>(this.order);
            ConfigManager.save();
            rebuild();
        }).bounds(xLeft + actionW + 10, height - 50, actionW, 20).build());
        addDrawableChild(Button.builder(Component.literal("Back"), btn -> close())
                .bounds(width / 2 - 60, height - 25, 120, 20).build());
    }

    private void moveUp(final int i) {
        if (i <= 0) return;
        final CardId moved = order.remove(i);
        order.add(i - 1, moved);
        ConfigManager.get().cardOrder = new ArrayList<>(order);
        ConfigManager.save();
        rebuild();
    }

    private void moveDown(final int i) {
        if (i >= order.size() - 1) return;
        final CardId moved = order.remove(i);
        order.add(i + 1, moved);
        ConfigManager.get().cardOrder = new ArrayList<>(order);
        ConfigManager.save();
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    @Override
    public void render(final GuiGraphics ctx, final int mouseX, final int mouseY, final float delta) {
        CardEffects.renderGradient(ctx, width, height, CardEffects.BG_TOP, CardEffects.BG_BOTTOM);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredString(font,
                Component.literal("CARDS — order & visibility").formatted(ChatFormatting.GOLD),
                width / 2, 25, 0xFFFFFFFF);

        // Card names rendered on top of buttons.
        final int rowH = 22;
        final int gap = 4;
        final int contentW = Math.min(width - 40, 420);
        final int xLeft = width / 2 - contentW / 2;
        final int yTop = 50;
        final int maxRows = Math.max(0, (height - yTop - 60) / (rowH + gap));
        final int visibleRows = Math.min(order.size(), maxRows);
        for (int i = 0; i < visibleRows; i++) {
            final CardId id = order.get(i);
            final int y = yTop + i * (rowH + gap);
            final String num = String.format("%2d.", i + 1);
            ctx.drawString(font, Component.literal(num + " " + id.displayName()),
                    xLeft + 110, y + 7, 0xFFE5E7EB);
        }
    }

    @Override
    public void renderBackground(final GuiGraphics ctx, final int mouseX, final int mouseY, final float delta) {
        // Painted in render().
    }

    @Override
    public void close() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(parent);
    }
}
