package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.WrappedSounds;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TopBlocksCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int FIRST_ROW_START = 14;
    private static final int ROW_STAGGER = 12;
    private static final int ROW_DURATION = 16;
    private static final int HOLD_END = 130;

    private final WrappedContext context;
    private final List<Map.Entry<String, Long>> top;
    private final ItemStack[] icons;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public TopBlocksCard(final WrappedContext context) {
        this.context = context;
        this.top = context.topMined(3);
        this.icons = new ItemStack[top.size()];
        for (int i = 0; i < top.size(); i++) {
            icons[i] = stackFor(top.get(i).getKey());
        }
    }

    @Override
    public void start(final Minecraft client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(14, width, height);
        WrappedSounds.play(client, SoundEvents.BLOCK_STONE_BREAK, 1.4f, 0.6f);
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);
        for (int i = 0; i < top.size(); i++) {
            if (ticks == FIRST_ROW_START + i * ROW_STAGGER) {
                WrappedSounds.play(Minecraft.getInstance(), SoundEvents.BLOCK_STONE_BREAK, 1.0f + i * 0.3f, 0.5f);
            }
        }
    }

    @Override
    public void render(final GuiGraphics ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final Font tr = Minecraft.getInstance().font;

        CardEffects.renderGradient(ctx, width, height, CardEffects.bgTop(), CardEffects.bgBottom());
        sparkles.render(ctx, partial);

        CardEffects.renderKicker(ctx, tr, width, 50, "TOP BLOCKS MINED", now, KICKER_START, KICKER_DURATION);

        if (top.isEmpty()) {
            renderEmpty(ctx, tr, width, height, now);
        } else {
            for (int i = 0; i < top.size(); i++) {
                renderRow(ctx, tr, width, height, now, i, top.get(i), icons[i]);
            }
        }

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderEmpty(final GuiGraphics ctx, final Font tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - FIRST_ROW_START) / 12f);
        if (t <= 0) return;
        final int alpha = (int) (t * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
        ctx.drawCenteredString(tr, Component.literal("nothing mined this month"), width / 2, height / 2, color);
    }

    private void renderRow(final GuiGraphics ctx, final Font tr, final int width, final int height, final float now,
                           final int index, final Map.Entry<String, Long> entry, final ItemStack icon) {
        final float t = CardEffects.clamp01((now - FIRST_ROW_START - index * ROW_STAGGER) / (float) ROW_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);

        final int rowHeight = 64;
        final int totalRowsHeight = top.size() * rowHeight + (top.size() - 1) * 16;
        final int rowsTop = height / 2 - totalRowsHeight / 2 + 10;
        final int yBase = rowsTop + index * (rowHeight + 16);

        final int slideOffset = (int) ((1f - ease) * 80);
        final int alpha = (int) (ease * 255) & 0xFF;

        final int rowX = width / 2 - 240;
        final int x = rowX + slideOffset;

        // Subtle glass card background per row.
        final int cardBg = (Math.min(alpha, 30) << 24) | 0xFFFFFF;
        ctx.fill(x - 8, yBase - 6, x + 480, yBase + rowHeight - 6, cardBg);

        // Rank number, large.
        final String rank = "#" + (index + 1);
        final float rankScale = 3.4f;
        final int rankColor = (alpha << 24) | (CardEffects.ACCENT_GOLD & 0xFFFFFF);
        ctx.pose().pushPose();
        ctx.pose().scale(rankScale, rankScale);
        ctx.drawString(tr, rank,
                (int) (x / rankScale),
                (int) ((yBase + 12) / rankScale),
                rankColor, true);
        ctx.pose().popPose();

        // Real block texture, 32x32 (item is 16, scale 2).
        final int iconCx = x + 110;
        final int iconCy = yBase + rowHeight / 2 - 6;
        if (!icon.isEmpty()) {
            ctx.pose().pushPose();
            ctx.pose().translate(iconCx, iconCy);
            ctx.pose().scale(2.4f, 2.4f);
            ctx.renderItem(icon, -8, -8);
            ctx.pose().popPose();
        }

        // Block name.
        final int textX = iconCx + 32;
        final Component name = blockName(entry.getKey());
        final float nameScale = 1.7f;
        final int nameColor = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);
        ctx.pose().pushPose();
        ctx.pose().scale(nameScale, nameScale);
        ctx.drawString(tr, name,
                (int) (textX / nameScale),
                (int) ((yBase + 14) / nameScale),
                nameColor, true);
        ctx.pose().popPose();

        // Count, right-aligned.
        final String count = formatCount(entry.getValue());
        final float countScale = 2.4f;
        final int countWidth = (int) (tr.getWidth(count) * countScale);
        final int countX = width / 2 + 240 - countWidth - 8;
        final int countColor = (alpha << 24) | (CardEffects.accent() & 0xFFFFFF);
        ctx.pose().pushPose();
        ctx.pose().scale(countScale, countScale);
        ctx.drawString(tr, count,
                (int) (countX / countScale),
                (int) ((yBase + 12) / countScale),
                countColor, true);
        ctx.pose().popPose();
    }

    private static ItemStack stackFor(final String blockId) {
        final ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) return ItemStack.EMPTY;
        final var item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private static Component blockName(final String id) {
        final String[] parts = id.split(":");
        if (parts.length != 2) return Component.literal(prettify(id));
        return Component.translatable("block." + parts[0] + "." + parts[1]);
    }

    private static String prettify(final String id) {
        final String stripped = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return stripped.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static String formatCount(final long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        return Long.toString(n);
    }
}
