package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.WrappedSounds;
import fr.zeffut.mcwrapped.archetype.Archetype;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class ArchetypeCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int DRUM_START = 14;
    private static final int DRUM_END = 54;
    private static final int FLIP_START = 54;
    private static final int FLIP_DURATION = 18;
    private static final int REVEAL_HOLD_END = 150;
    private static final int FADE_OUT = 18;

    private static final int[] DRUM_BEATS = {18, 26, 34, 42, 50};

    private final WrappedContext context;
    private final Archetype archetype;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public ArchetypeCard(final WrappedContext context) {
        this.context = context;
        this.archetype = Archetype.pick(context);
    }

    public Archetype archetype() { return archetype; }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(20, width, height);
        WrappedSounds.play(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 0.4f);
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);

        // Drum roll buildup.
        for (int i = 0; i < DRUM_BEATS.length; i++) {
            if (ticks == DRUM_BEATS[i]) {
                final float pitch = 0.8f + i * 0.15f;
                WrappedSounds.play(MinecraftClient.getInstance(), SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), pitch, 0.7f);
            }
        }
        if (ticks == FLIP_START + FLIP_DURATION / 2) {
            WrappedSounds.play(MinecraftClient.getInstance(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.6f);
        }
    }

    @Override
    public void render(final DrawContext ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        CardEffects.renderGradient(ctx, width, height, CardEffects.bgTop(), CardEffects.bgBottom());
        sparkles.render(ctx, partial);

        final boolean revealed = now >= FLIP_START + FLIP_DURATION / 2f;
        renderHaloPulse(ctx, width, height, now, revealed);

        CardEffects.renderKicker(ctx, tr, width, height / 2 - 100, "YOUR ARCHETYPE", now, KICKER_START, KICKER_DURATION);

        renderFlipCard(ctx, tr, width, height, now);

        CardEffects.renderFadeOut(ctx, width, height, now, REVEAL_HOLD_END, FADE_OUT);
    }

    @Override
    public boolean isDone() {
        return ticks >= REVEAL_HOLD_END + FADE_OUT;
    }

    // ---------- Layers ----------

    private void renderHaloPulse(final DrawContext ctx, final int width, final int height, final float now, final boolean revealed) {
        final int cx = width / 2;
        final int cy = height / 2 + 8;
        // Drum buildup pulses: each drum beat triggers a fading expanding ring.
        for (final int beat : DRUM_BEATS) {
            final float age = now - beat;
            if (age <= 0 || age > 18) continue;
            final float ease = age / 18f;
            final int radiusX = (int) (60 + ease * 240);
            final int radiusY = (int) (35 + ease * 110);
            final int alpha = (int) ((1 - ease) * 80) & 0xFF;
            CardEffects.drawRectBorder(ctx, cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY, 1,
                    (alpha << 24) | (CardEffects.ACCENT_GOLD & 0xFFFFFF));
        }
        // Reveal halo.
        if (revealed) {
            CardEffects.renderHalo(ctx, cx, cy, now, CardEffects.ACCENT_GOLD);
        }
    }

    private void renderFlipCard(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        // Card geometry.
        final int cardW = 360;
        final int cardH = 140;
        final int cx = width / 2;
        final int cy = height / 2 + 8;

        // Compute scaleX based on flip phase.
        float scaleX = 1f;
        boolean showBack = false;
        if (now < FLIP_START) {
            // Drum roll: card jiggle slightly.
            final float wobble = (float) Math.sin(now * 0.6f) * 0.02f;
            scaleX = 1f + wobble;
            showBack = false;
        } else if (now < FLIP_START + FLIP_DURATION) {
            final float t = (now - FLIP_START) / FLIP_DURATION;
            if (t < 0.5f) {
                scaleX = Easing.EASE_IN_QUAD.apply(1f - t * 2f);
                showBack = false;
            } else {
                scaleX = Easing.EASE_OUT_QUAD.apply((t - 0.5f) * 2f);
                showBack = true;
            }
        } else {
            scaleX = 1f;
            showBack = true;
            // Subtle breathing once revealed.
            scaleX *= 1f + 0.01f * (float) Math.sin((now - FLIP_START - FLIP_DURATION) * 0.15f);
        }

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0f);
        ctx.getMatrices().scale(Math.max(0.001f, Math.abs(scaleX)), 1f, 1f);
        // Card background.
        final int bgColor = showBack ? 0xFF12122A : 0xFF1A1A40;
        ctx.fill(-cardW / 2, -cardH / 2, cardW / 2, cardH / 2, bgColor);
        // Border (gold when revealed, indigo before).
        final int borderColor = showBack ? CardEffects.ACCENT_GOLD : CardEffects.accentSecondary();
        CardEffects.drawRectBorder(ctx, -cardW / 2, -cardH / 2, cardW / 2, cardH / 2, 2, borderColor);

        if (showBack) {
            renderCardBack(ctx, tr);
        } else {
            renderCardFront(ctx, tr);
        }
        ctx.getMatrices().pop();
    }

    private void renderCardFront(final DrawContext ctx, final TextRenderer tr) {
        // Big "?".
        final String q = "?";
        final float scale = 6f;
        final int width = (int) (tr.getWidth(q) * scale);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, q, (int) ((-width / 2f) / scale), (int) ((-5 * scale / 2f) / scale), 0xFFCBD5E1, true);
        ctx.getMatrices().pop();
    }

    private void renderCardBack(final DrawContext ctx, final TextRenderer tr) {
        final String name = archetype.displayName().toUpperCase(java.util.Locale.ROOT);
        final String tagline = archetype.tagline();

        // Name (auto-shrink scale to fit width).
        float scale = 1.8f;
        int w = (int) (tr.getWidth(name) * scale);
        while (w > 320 && scale > 0.9f) {
            scale -= 0.1f;
            w = (int) (tr.getWidth(name) * scale);
        }
        ctx.getMatrices().push();
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, name,
                (int) ((-w / 2f) / scale),
                (int) ((-22) / scale),
                CardEffects.ACCENT_GOLD, true);
        ctx.getMatrices().pop();

        // Tagline.
        final int tw = tr.getWidth(tagline);
        ctx.drawText(tr, tagline, -tw / 2, 8, CardEffects.TEXT_DIM | 0xFF000000, true);
    }
}
