package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.stats.TimeOfDayTracker;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class TimeOfDayCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int BARS_START = 14;
    private static final int BARS_DURATION = 30;
    private static final int LABEL_START = 36;
    private static final int LABEL_DURATION = 14;
    private static final int HOLD_END = 120;

    private final WrappedContext context;
    private final long[] hours;
    private final int peakHour;
    private final long maxBucket;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public TimeOfDayCard(final WrappedContext context) {
        this.context = context;
        if (TimeOfDayTracker.INSTANCE != null) {
            this.hours = TimeOfDayTracker.INSTANCE.forMonth(context.month());
            this.peakHour = TimeOfDayTracker.INSTANCE.peakHour(context.month());
        } else {
            this.hours = new long[24];
            this.peakHour = 0;
        }
        long max = 0;
        for (final long v : hours) if (v > max) max = v;
        this.maxBucket = max;
    }

    public boolean hasData() {
        for (final long v : hours) if (v > 0) return true;
        return false;
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(14, width, height);
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 1.0f, 0.5f));
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);
    }

    @Override
    public void render(final DrawContext ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        CardEffects.renderGradient(ctx, width, height, CardEffects.BG_TOP, CardEffects.BG_BOTTOM);
        sparkles.render(ctx, partial);

        CardEffects.renderKicker(ctx, tr, width, height / 2 - 110, "WHEN YOU PLAY", now, KICKER_START, KICKER_DURATION);

        renderBars(ctx, tr, width, height, now);
        renderPeakLabel(ctx, tr, width, height, now);

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderBars(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final int barAreaW = 480;
        final int gap = 4;
        final int barW = (barAreaW - 23 * gap) / 24;
        final int maxBarH = 110;
        final int xLeft = width / 2 - barAreaW / 2;
        final int yBottom = height / 2 + 30;

        for (int h = 0; h < 24; h++) {
            final float bt = CardEffects.clamp01((now - BARS_START - h * 0.6f) / (float) BARS_DURATION);
            if (bt <= 0) continue;
            final float ease = Easing.EASE_OUT_CUBIC.apply(bt);
            final float ratio = maxBucket > 0 ? hours[h] / (float) maxBucket : 0f;
            final int barH = (int) (maxBarH * ratio * ease);
            final int alpha = (int) (ease * 255) & 0xFF;
            final int color = h == peakHour
                    ? ((alpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF))
                    : ((alpha << 24) | (CardEffects.ACCENT_INDIGO & 0xFFFFFF));
            final int x = xLeft + h * (barW + gap);
            ctx.fill(x, yBottom - barH, x + barW, yBottom, color);
        }

        // Hour ticks beneath bars (every 6h labeled).
        final int alpha = (int) (CardEffects.clamp01((now - BARS_START) / 12f) * 200) & 0xFF;
        final int labelColor = (alpha << 24) | (CardEffects.TEXT_KICKER & 0xFFFFFF);
        for (int h = 0; h < 24; h += 6) {
            final int x = xLeft + h * (barW + gap);
            ctx.drawTextWithShadow(tr, Text.literal(String.format("%02dh", h)), x, yBottom + 4, labelColor);
        }
    }

    private void renderPeakLabel(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        if (!hasData()) return;
        final float t = CardEffects.clamp01((now - LABEL_START) / (float) LABEL_DURATION);
        if (t <= 0) return;
        final int alpha = (int) (t * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF);
        final String label = String.format("peak hour: %02dh — %s", peakHour, peakLabelText(peakHour));
        ctx.drawCenteredTextWithShadow(tr, Text.literal(label), width / 2, height / 2 + 80, color);
    }

    private static String peakLabelText(final int h) {
        if (h < 6) return "night owl";
        if (h < 11) return "morning grinder";
        if (h < 14) return "lunch raider";
        if (h < 18) return "afternoon hero";
        if (h < 22) return "evening ritual";
        return "midnight builder";
    }
}
