package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.WrappedSounds;
import fr.zeffut.mcwrapped.stats.SessionTracker;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Locale;

public final class LongestSessionCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int COUNTER_START = 14;
    private static final int COUNTER_DURATION = 28;
    private static final int SUB_START = 28;
    private static final int SUB_DURATION = 12;
    private static final int HOLD_END = 110;

    private final WrappedContext context;
    private final long longestMs;
    private final int sessionCount;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public LongestSessionCard(final WrappedContext context) {
        this.context = context;
        if (SessionTracker.INSTANCE != null) {
            this.longestMs = SessionTracker.INSTANCE.longestSessionMsInMonth(context.month());
            this.sessionCount = SessionTracker.INSTANCE.sessionCountInMonth(context.month());
        } else {
            this.longestMs = 0L;
            this.sessionCount = 0;
        }
    }

    public boolean hasData() { return longestMs > 0; }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(16, width, height);
        WrappedSounds.play(client, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.9f, 0.4f);
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
        CardEffects.renderHalo(ctx, width / 2, height / 2, now, CardEffects.accent());

        CardEffects.renderKicker(ctx, tr, width, height / 2 - 70, "LONGEST SESSION", now, KICKER_START, KICKER_DURATION);

        renderHero(ctx, tr, width, height, now);
        renderSub(ctx, tr, width, height, now);

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderHero(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - COUNTER_START) / (float) COUNTER_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final long shownMs = Math.round(ease * longestMs);

        final long minutes = shownMs / 60_000;
        final String text = (minutes >= 60)
                ? (minutes / 60) + "h " + String.format(Locale.ROOT, "%02d", minutes % 60) + "m"
                : minutes + "m";

        final float scale = 5.0f;
        final int textWidth = tr.getWidth(text);
        final int alpha = (int) (CardEffects.clamp01(t * 1.5f) * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, text,
                (int) ((width / 2f - textWidth * scale / 2f) / scale),
                (int) ((height / 2f - 5 * scale / 2f) / scale),
                color, true);
        ctx.getMatrices().popMatrix();
    }

    private void renderSub(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - SUB_START) / (float) SUB_DURATION);
        if (t <= 0) return;
        final int alpha = (int) (t * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.accent() & 0xFFFFFF);
        final String label = sessionCount + (sessionCount == 1 ? " session" : " sessions") + " this month";
        ctx.drawCenteredTextWithShadow(tr, Text.literal(label), width / 2, height / 2 + 70, color);
    }
}
