package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.WrappedSounds;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class TimeSpentCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int CLOCK_START = 8;
    private static final int CLOCK_DURATION = 20;
    private static final int COUNTER_START = 14;
    private static final int COUNTER_DURATION = 36;
    private static final int SUB_START = 50;
    private static final int SUB_DURATION = 12;
    private static final int HOLD_END = 110;

    private final WrappedContext context;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public TimeSpentCard(final WrappedContext context) {
        this.context = context;
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(18, width, height);
        WrappedSounds.play(client, SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), 1.5f, 0.4f);
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

        CardEffects.renderGradient(ctx, width, height, CardEffects.bgTop(), CardEffects.bgBottom());
        sparkles.render(ctx, partial);

        renderClock(ctx, width, height, now);
        CardEffects.renderKicker(ctx, tr, width, height / 2 - 130, "TIME SPENT", now, KICKER_START, KICKER_DURATION);
        renderCounter(ctx, tr, width, height, now);
        renderSub(ctx, tr, width, height, now);

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderClock(final DrawContext ctx, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - CLOCK_START) / (float) CLOCK_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 200) & 0xFF;

        final int cx = width / 2;
        final int cy = height / 2 + 5;
        final int radius = 110;

        // 60 tick marks; major every 5 (the 12 hour marks).
        for (int i = 0; i < 60; i++) {
            final float angle = i * (float) (Math.PI / 30) - (float) Math.PI / 2;
            final boolean major = i % 5 == 0;
            final int len = major ? 10 : 4;
            final int innerR = radius - len;
            final int x1 = (int) (cx + Math.cos(angle) * innerR);
            final int y1 = (int) (cy + Math.sin(angle) * innerR);
            final int x2 = (int) (cx + Math.cos(angle) * radius);
            final int y2 = (int) (cy + Math.sin(angle) * radius);
            final int markColor = (alpha << 24) | ((major ? 0xCBD5E1 : 0x475569));
            CardEffects.drawRotatedLine(ctx, x1, y1, x2, y2, major ? 3 : 2, markColor);
        }

        // Counter-hand: rotates fast during count-up, settles on final position.
        final float counterT = CardEffects.clamp01((now - COUNTER_START) / (float) COUNTER_DURATION);
        if (counterT > 0) {
            final float counterEase = Easing.EASE_OUT_CUBIC.apply(counterT);
            final long totalMinutes = Math.max(0, context.playTimeTicks() / 20 / 60);
            final long minutesMod60 = totalMinutes % 60;
            final float finalAngle = minutesMod60 / 60f * (float) (Math.PI * 2);
            final float spinAngle = (1f - counterEase) * (float) (Math.PI * 6); // 3 fast rotations during ramp-up
            final float angle = finalAngle + spinAngle - (float) Math.PI / 2;
            final int handLen = (int) (radius * 0.85f);
            final int hx = (int) (cx + Math.cos(angle) * handLen);
            final int hy = (int) (cy + Math.sin(angle) * handLen);
            final int handColor = (alpha << 24) | (CardEffects.accent() & 0xFFFFFF);
            CardEffects.drawRotatedLine(ctx, cx, cy, hx, hy, 3, handColor);
        }

        // Center dot.
        final int dotColor = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);
        ctx.fill(cx - 4, cy - 4, cx + 4, cy + 4, dotColor);
    }

    private void renderCounter(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - COUNTER_START) / (float) COUNTER_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);

        final long totalMinutes = Math.max(0, context.playTimeTicks() / 20 / 60);
        final long shownMinutes = Math.round(ease * totalMinutes);

        final String text;
        if (totalMinutes >= 60) {
            text = (shownMinutes / 60) + "h " + String.format("%02d", shownMinutes % 60) + "m";
        } else {
            text = shownMinutes + "m";
        }

        final float scale = 3.6f;
        final int textWidth = tr.getWidth(text);
        final int alpha = (int) (CardEffects.clamp01(t * 1.5f) * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, text,
                (int) ((width / 2f - textWidth * scale / 2f) / scale),
                (int) ((height / 2f + 5 - 5 * scale / 2f) / scale),
                color, true);
        ctx.getMatrices().popMatrix();
    }

    private void renderSub(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - SUB_START) / (float) SUB_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.accent() & 0xFFFFFF);
        final int yOffset = (int) ((1f - ease) * 6);

        final String hint = funFact(context.playTimeTicks());
        ctx.drawCenteredTextWithShadow(tr, Text.literal(hint), width / 2, height / 2 + 135 + yOffset, color);
    }

    private static String funFact(final long playTimeTicks) {
        final long minutes = playTimeTicks / 20 / 60;
        if (minutes < 30) return "a quick visit";
        if (minutes < 120) return "a casual session";
        if (minutes < 600) return "a serious month";
        if (minutes < 2400) return "you really live here";
        return "this is your second home";
    }
}
