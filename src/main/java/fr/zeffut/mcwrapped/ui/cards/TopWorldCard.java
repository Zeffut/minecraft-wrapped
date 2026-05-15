package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.WrappedSounds;
import fr.zeffut.mcwrapped.stats.WorldKey;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public final class TopWorldCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int FRAME_START = 12;
    private static final int FRAME_DURATION = 16;
    private static final int NAME_START = 22;
    private static final int NAME_DURATION = 14;
    private static final int BAR_START = 30;
    private static final int BAR_DURATION = 22;
    private static final int LABEL_START = 38;
    private static final int LABEL_DURATION = 12;
    private static final int HOLD_END = 120;

    private final WrappedContext context;
    private final List<Map.Entry<String, Long>> top;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public TopWorldCard(final WrappedContext context) {
        this.context = context;
        this.top = context.topWorlds(3);
    }

    @Override
    public void start(final Minecraft client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(14, width, height);
        WrappedSounds.play(client, SoundEvents.BLOCK_CHEST_OPEN, 1.4f, 0.4f);
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);
    }

    @Override
    public void render(final GuiGraphics ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final Font tr = Minecraft.getInstance().font;

        CardEffects.renderGradient(ctx, width, height, CardEffects.bgTop(), CardEffects.bgBottom());
        sparkles.render(ctx, partial);
        renderMapGridBackground(ctx, width, height, now);

        CardEffects.renderKicker(ctx, tr, width, height / 2 - 110, "TOP WORLD", now, KICKER_START, KICKER_DURATION);

        if (top.isEmpty()) {
            renderEmpty(ctx, tr, width, height, now);
        } else {
            final Map.Entry<String, Long> first = top.get(0);
            renderFrame(ctx, width, height, now);
            renderName(ctx, tr, width, height, now, first.getKey(), first.getValue());
            renderShareBar(ctx, tr, width, height, now, first.getValue());
        }

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private static String spaceLetters(final String s) {
        if (s.isEmpty()) return s;
        final StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (i < s.length() - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private void renderMapGridBackground(final GuiGraphics ctx, final int width, final int height, final float now) {
        // Subtle dotted-grid pattern, fades in over time; evokes a treasure map.
        final float t = CardEffects.clamp01((now - FRAME_START) / 18f);
        if (t <= 0) return;
        final int alpha = (int) (t * 18) & 0xFF;
        if (alpha == 0) return;
        final int color = (alpha << 24) | 0xFFFFFF;
        for (int y = 20; y < height - 20; y += 24) {
            for (int x = 20; x < width - 20; x += 24) {
                ctx.fill(x, y, x + 2, y + 2, color);
            }
        }
    }

    private void renderEmpty(final GuiGraphics ctx, final Font tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - NAME_START) / 12f);
        if (t <= 0) return;
        final int alpha = (int) (t * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
        ctx.drawCenteredString(tr, Component.literal("no worlds tracked"), width / 2, height / 2, color);
    }

    private void renderFrame(final GuiGraphics ctx, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - FRAME_START) / (float) FRAME_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);

        final int frameW = 460;
        final int frameH = 110;
        final int x0 = width / 2 - frameW / 2;
        final int y0 = height / 2 - frameH / 2 - 5;
        final int x1 = x0 + frameW;
        final int y1 = y0 + frameH;

        // Trace the border progressively (top, right, bottom, left).
        final int alpha = (int) (ease * 220) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.ACCENT_GOLD & 0xFFFFFF);
        final float perim = 2 * (frameW + frameH);
        final float drawn = ease * perim;

        // Top edge
        final int topLen = (int) Math.min(drawn, frameW);
        ctx.fill(x0, y0, x0 + topLen, y0 + 2, color);
        // Right edge
        final int rightLen = (int) Math.max(0, Math.min(drawn - frameW, frameH));
        ctx.fill(x1 - 2, y0, x1, y0 + rightLen, color);
        // Bottom edge
        final int bottomLen = (int) Math.max(0, Math.min(drawn - frameW - frameH, frameW));
        ctx.fill(x1 - bottomLen, y1 - 2, x1, y1, color);
        // Left edge
        final int leftLen = (int) Math.max(0, Math.min(drawn - 2 * frameW - frameH, frameH));
        ctx.fill(x0, y1 - leftLen, x0 + 2, y1, color);

        // Inner glass tint.
        final int glassAlpha = (int) (ease * 22) & 0xFF;
        ctx.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, (glassAlpha << 24) | 0xFFFFFF);
    }

    private void renderName(final GuiGraphics ctx, final Font tr, final int width, final int height,
                            final float now, final String worldKey, final long playTimeTicks) {
        final float t = CardEffects.clamp01((now - NAME_START) / (float) NAME_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 255) & 0xFF;
        final int yOffset = (int) ((1f - ease) * 8);

        final String worldName = WorldKey.displayNameMasked(worldKey, 1);
        final boolean isServer = WorldKey.isServer(worldKey);

        // Small SOURCE badge, floating above the frame as a tag chip.
        final String badge = WorldKey.badge(worldKey);
        final int badgeColor = (alpha << 24) | ((isServer ? CardEffects.accentSecondary() : CardEffects.accent()) & 0xFFFFFF);
        ctx.drawCenteredString(tr, Component.literal(spaceLetters(badge)), width / 2, height / 2 - 78 + yOffset, badgeColor);

        // World name (truncated if too long). Vertically centered with frame center (height/2 - 5).
        final String displayName = worldName.length() > 22 ? worldName.substring(0, 21) + "…" : worldName;
        final float nameScale = 2.6f;
        final int nameWidth = (int) (tr.getWidth(displayName) * nameScale);
        final int nameX = width / 2 - nameWidth / 2;
        final int nameY = height / 2 - 24 + yOffset;
        final int nameColor = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);
        ctx.pose().pushPose();
        ctx.pose().scale(nameScale, nameScale);
        ctx.drawString(tr, displayName, (int) (nameX / nameScale), (int) (nameY / nameScale), nameColor, true);
        ctx.pose().popPose();

        // Play time inside the frame, dimmer.
        final long minutes = playTimeTicks / 20 / 60;
        final String timeText = (minutes >= 60)
                ? (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m here"
                : minutes + "m here";
        final int timeColor = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
        ctx.drawCenteredString(tr, Component.literal(timeText), width / 2, nameY + 30, timeColor);
    }

    private void renderShareBar(final GuiGraphics ctx, final Font tr, final int width, final int height,
                                 final float now, final long topPlayTime) {
        final float bt = CardEffects.clamp01((now - BAR_START) / (float) BAR_DURATION);
        if (bt <= 0) return;
        final float barEase = Easing.EASE_OUT_CUBIC.apply(bt);

        // Compute the share — cap at 1 to be safe with truncated subsequent worlds.
        final long totalRecorded = top.stream().mapToLong(Map.Entry::getValue).sum();
        final long denom = Math.max(totalRecorded, 1L);
        final float share = Math.min(1f, topPlayTime / (float) denom);
        final float fillT = barEase * share;

        final int barWidth = 360;
        final int barX = width / 2 - barWidth / 2;
        final int barY = height / 2 + 80;
        final int barH = 6;

        // Track (dim).
        ctx.fill(barX, barY, barX + barWidth, barY + barH, 0x4044556B);
        // Fill (green).
        final int filled = (int) (barWidth * fillT);
        ctx.fill(barX, barY, barX + filled, barY + barH, CardEffects.accent());

        // Label.
        final float lt = CardEffects.clamp01((now - LABEL_START) / (float) LABEL_DURATION);
        if (lt > 0) {
            final int alpha = (int) (lt * 255) & 0xFF;
            final int pct = Math.round(share * 100f);
            final String suffix = top.size() > 1
                    ? "% of your month, across " + context.worldsCount() + " worlds"
                    : "% of your month";
            final String label = pct + suffix;
            final int color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
            ctx.drawCenteredString(tr, Component.literal(label), width / 2, barY + 16, color);
        }
    }
}
