package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Random;

/**
 * Wrapped intro: night-indigo gradient + halo + cascading month reveal.
 *
 * <p>Design DNA: bold kicker + giant month + accent footer with underline draw-in.
 * Inspired by Spotify Wrapped 2024's hero treatment, palette #0F0F23 / #1E1B4B / #22C55E.
 */
public final class IntroCard implements Card {

    // --- Timeline (ticks @ 20Hz) ---
    private static final int KICKER_IN_START = 4;
    private static final int KICKER_IN_DURATION = 10;
    private static final int SWEEP_START = 12;
    private static final int SWEEP_DURATION = 8;
    private static final int LETTER_START = 16;
    private static final int LETTER_STAGGER = 3;
    private static final int LETTER_DURATION = 12;
    private static final int FOOTER_START = 36;
    private static final int FOOTER_DURATION = 10;
    private static final int UNDERLINE_START = 40;
    private static final int UNDERLINE_DURATION = 14;
    private static final int HOLD_END = 110;
    private static final int FADE_OUT_DURATION = 18;
    private static final int TOTAL_TICKS = HOLD_END + FADE_OUT_DURATION;

    // --- Colors (ARGB) ---
    private static final int BG_TOP = 0xFF0F0F23;
    private static final int BG_BOTTOM = 0xFF1E1B4B;
    private static final int ACCENT_GREEN = 0xFF22C55E;
    private static final int ACCENT_INDIGO = 0xFF4338CA;
    private static final int TEXT_HERO = 0xFFF8FAFC;
    private static final int TEXT_KICKER = 0xFF94A3B8;
    private static final int TEXT_FOOTER = ACCENT_GREEN;

    private final String monthName;
    private final String yearLabel;
    private final Random rng = new Random(0xCAFE);

    private int ticks = 0;
    private boolean started = false;
    private Sparkle[] sparkles;

    public IntroCard(final YearMonth month) {
        this.monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        this.yearLabel = month.getYear() + " WRAPPED";
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        client.getSoundManager().play(
                PositionedSoundInstance.ui(SoundEvents.ENTITY_ENDER_PEARL_THROW, 1.0f, 0.5f));
        sparkles = new Sparkle[18];
        for (int i = 0; i < sparkles.length; i++) {
            sparkles[i] = new Sparkle(rng, width, height);
        }
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        for (final Sparkle s : sparkles) s.tick(rng, width, height);

        if (ticks == LETTER_START) {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.2f, 0.7f));
        }
        if (ticks == FOOTER_START) {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f, 0.6f));
        }
    }

    @Override
    public void render(final DrawContext ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        renderGradient(ctx, width, height);
        renderSparkles(ctx, partial);
        renderHalo(ctx, width, height, now);
        renderSweep(ctx, width, height, now);
        renderKicker(ctx, tr, width, height, now);
        renderHeroMonth(ctx, tr, width, height, now);
        renderFooter(ctx, tr, width, height, now);
        renderFadeOut(ctx, width, height, now);
    }

    @Override
    public boolean isDone() {
        return ticks >= TOTAL_TICKS;
    }

    // ---------- Layers ----------

    private void renderGradient(final DrawContext ctx, final int width, final int height) {
        // Vertical interp from BG_TOP to BG_BOTTOM via thin horizontal strips.
        final int strips = 64;
        for (int i = 0; i < strips; i++) {
            final float t = i / (float) (strips - 1);
            final int color = lerpColor(BG_TOP, BG_BOTTOM, t);
            final int y0 = i * height / strips;
            final int y1 = (i + 1) * height / strips;
            ctx.fill(0, y0, width, y1, color);
        }
    }

    private void renderSparkles(final DrawContext ctx, final float partial) {
        for (final Sparkle s : sparkles) {
            final int a = Math.round(s.alpha(partial) * 255f) & 0xFF;
            if (a == 0) continue;
            final int color = (a << 24) | (0xFFFFFF);
            ctx.fill(s.x, s.y, s.x + s.size, s.y + s.size, color);
        }
    }

    private void renderHalo(final DrawContext ctx, final int width, final int height, final float now) {
        // 5 concentric square rings centered on the hero word, breathing scale.
        final float breathe = 1f + 0.015f * (float) Math.sin(now * 0.18f);
        final int cx = width / 2;
        final int cy = height / 2 - 2;
        final int[] alphas = {30, 20, 14, 9, 6};
        final int[] sizes = {120, 180, 250, 330, 420};
        for (int i = 0; i < alphas.length; i++) {
            final int s = (int) (sizes[i] * breathe);
            final int a = alphas[i];
            final int color = (a << 24) | (TEXT_HERO & 0xFFFFFF);
            drawRectBorder(ctx, cx - s / 2, cy - s / 4, cx + s / 2, cy + s / 4, 1, color);
        }
    }

    private void renderSweep(final DrawContext ctx, final int width, final int height, final float now) {
        // Horizontal accent line that grows from center, then fades.
        final float t = (now - SWEEP_START) / (float) SWEEP_DURATION;
        if (t <= 0) return;
        final float growth = clamp01(t);
        final float fade = 1f - clamp01((t - 1f) * 0.5f);
        if (fade <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(growth);
        final int halfW = (int) (ease * (width / 2 - 20));
        final int cx = width / 2;
        final int cy = height / 2;
        final int alpha = (int) (fade * 220) & 0xFF;
        final int color = (alpha << 24) | (ACCENT_GREEN & 0xFFFFFF);
        ctx.fill(cx - halfW, cy - 1, cx + halfW, cy + 1, color);
        // Soft glow line above/below at lower alpha.
        final int glowAlpha = (int) (fade * 60) & 0xFF;
        final int glow = (glowAlpha << 24) | (ACCENT_GREEN & 0xFFFFFF);
        ctx.fill(cx - halfW, cy - 3, cx + halfW, cy - 2, glow);
        ctx.fill(cx - halfW, cy + 2, cx + halfW, cy + 3, glow);
    }

    private void renderKicker(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = clamp01((now - KICKER_IN_START) / (float) KICKER_IN_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 200) & 0xFF;
        final int color = (alpha << 24) | (TEXT_KICKER & 0xFFFFFF);

        final String text = "Y O U R   M I N E C R A F T";
        final int yBase = height / 2 - 70;
        final int yOffset = (int) ((1f - ease) * 8);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(text), width / 2, yBase + yOffset, color);
    }

    private void renderHeroMonth(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float scale = 4.5f;
        final int totalWidth = (int) (tr.getWidth(monthName) * scale);
        final int startX = width / 2 - totalWidth / 2;
        final int baseY = height / 2 - (int) (5 * scale);

        // Subtle indigo shadow behind, drawn once at full opacity once the first letter starts.
        final float anyLetterT = clamp01((now - LETTER_START) / (float) LETTER_DURATION);
        if (anyLetterT > 0) {
            final int shadowAlpha = (int) (anyLetterT * 100) & 0xFF;
            final int shadowColor = (shadowAlpha << 24) | (ACCENT_INDIGO & 0xFFFFFF);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().scale(scale, scale);
            ctx.drawText(tr, monthName, (int) ((startX + 3) / scale), (int) ((baseY + 4) / scale), shadowColor, false);
            ctx.getMatrices().popMatrix();
        }

        float x = startX;
        for (int i = 0; i < monthName.length(); i++) {
            final String ch = String.valueOf(monthName.charAt(i));
            final int charPx = (int) (tr.getWidth(ch) * scale);
            final float letterDelay = LETTER_START + i * LETTER_STAGGER;
            final float t = clamp01((now - letterDelay) / (float) LETTER_DURATION);
            if (t > 0) {
                final float ease = Easing.EASE_OUT_CUBIC.apply(t);
                final int alpha = (int) (ease * 255) & 0xFF;
                final float yOffset = (1f - ease) * 28f;
                final int color = (alpha << 24) | (TEXT_HERO & 0xFFFFFF);
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().scale(scale, scale);
                ctx.drawText(tr, ch, (int) (x / scale), (int) ((baseY + yOffset) / scale), color, true);
                ctx.getMatrices().popMatrix();
            }
            x += charPx;
        }
    }

    private void renderFooter(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        // "2026 WRAPPED"
        final float ft = clamp01((now - FOOTER_START) / (float) FOOTER_DURATION);
        if (ft <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(ft);
        final int alpha = (int) (ease * 255) & 0xFF;
        final int color = (alpha << 24) | (TEXT_FOOTER & 0xFFFFFF);

        final float footerScale = 1.5f;
        final int footerWidth = (int) (tr.getWidth(yearLabel) * footerScale);
        final int yBase = height / 2 + 70;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(footerScale, footerScale);
        final int fx = (int) ((width / 2 - footerWidth / 2) / footerScale);
        final int fy = (int) (yBase / footerScale);
        ctx.drawText(tr, yearLabel, fx, fy, color, true);
        ctx.getMatrices().popMatrix();

        // Underline draws from center outward.
        final float ut = clamp01((now - UNDERLINE_START) / (float) UNDERLINE_DURATION);
        if (ut > 0) {
            final float ue = Easing.EASE_OUT_CUBIC.apply(ut);
            final int halfLen = (int) (ue * footerWidth / 2f);
            final int underlineY = yBase + (int) (10 * footerScale);
            final int cx = width / 2;
            ctx.fill(cx - halfLen, underlineY, cx + halfLen, underlineY + 2, TEXT_FOOTER);
        }
    }

    private void renderFadeOut(final DrawContext ctx, final int width, final int height, final float now) {
        if (now <= HOLD_END) return;
        final float t = clamp01((now - HOLD_END) / (float) FADE_OUT_DURATION);
        final int alpha = (int) (t * 255) & 0xFF;
        ctx.fill(0, 0, width, height, (alpha << 24));
    }

    // ---------- Helpers ----------

    private static void drawRectBorder(final DrawContext ctx, final int x0, final int y0, final int x1, final int y1, final int thickness, final int color) {
        ctx.fill(x0, y0, x1, y0 + thickness, color);
        ctx.fill(x0, y1 - thickness, x1, y1, color);
        ctx.fill(x0, y0, x0 + thickness, y1, color);
        ctx.fill(x1 - thickness, y0, x1, y1, color);
    }

    private static int lerpColor(final int a, final int b, final float t) {
        final int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        final int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        final int ra = (int) (aa + (ba - aa) * t);
        final int rr = (int) (ar + (br - ar) * t);
        final int rg = (int) (ag + (bg - ag) * t);
        final int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static float clamp01(final float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Ambient sparkle: tiny dot that fades in and out at random positions. */
    private static final class Sparkle {
        int x, y, size;
        int lifeStart;
        int lifeDuration;
        static int globalTick = 0;

        Sparkle(final Random rng, final int w, final int h) {
            respawn(rng, w, h);
        }

        void respawn(final Random rng, final int w, final int h) {
            this.x = rng.nextInt(Math.max(1, w));
            this.y = rng.nextInt(Math.max(1, h));
            this.size = 2 + rng.nextInt(2);
            this.lifeStart = globalTick;
            this.lifeDuration = 30 + rng.nextInt(50);
        }

        void tick(final Random rng, final int w, final int h) {
            globalTick++;
            if (globalTick - lifeStart > lifeDuration) {
                respawn(rng, w, h);
            }
        }

        float alpha(final float partial) {
            final float t = (globalTick - lifeStart + partial) / (float) lifeDuration;
            if (t < 0 || t > 1) return 0;
            // Triangle envelope: ramp up then ramp down, peak at t=0.5.
            final float env = t < 0.5f ? t * 2f : (1f - t) * 2f;
            return env * 0.45f;
        }
    }
}
