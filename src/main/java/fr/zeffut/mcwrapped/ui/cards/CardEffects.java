package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Random;

/**
 * Shared rendering helpers for cards: gradients, halo rings, sparkles, kicker text,
 * fade-out, and standard color/timing constants.
 */
public final class CardEffects {

    public static final int BG_TOP = 0xFF0F0F23;
    public static final int BG_BOTTOM = 0xFF1E1B4B;
    public static final int BG_TOP_RED = 0xFF1F0A0A;
    public static final int BG_BOTTOM_RED = 0xFF4C0E0E;

    public static final int ACCENT_GREEN = 0xFF22C55E;
    public static final int ACCENT_INDIGO = 0xFF4338CA;
    public static final int ACCENT_GOLD = 0xFFFCD34D;
    public static final int ACCENT_RED = 0xFFEF4444;

    public static final int TEXT_HERO = 0xFFF8FAFC;
    public static final int TEXT_KICKER = 0xFF94A3B8;
    public static final int TEXT_DIM = 0xFFCBD5E1;

    private CardEffects() {}

    public static void renderGradient(final DrawContext ctx, final int width, final int height,
                                      final int top, final int bottom) {
        final int strips = 64;
        for (int i = 0; i < strips; i++) {
            final float t = i / (float) (strips - 1);
            final int color = lerpColor(top, bottom, t);
            final int y0 = i * height / strips;
            final int y1 = (i + 1) * height / strips;
            ctx.fill(0, y0, width, y1, color);
        }
    }

    public static void renderHalo(final DrawContext ctx, final int cx, final int cy, final float now,
                                  final int baseColor) {
        final float breathe = 1f + 0.015f * (float) Math.sin(now * 0.18f);
        final int[] alphas = {30, 20, 14, 9, 6};
        final int[] sizes = {120, 180, 250, 330, 420};
        for (int i = 0; i < alphas.length; i++) {
            final int s = (int) (sizes[i] * breathe);
            final int a = alphas[i];
            final int color = (a << 24) | (baseColor & 0xFFFFFF);
            drawRectBorder(ctx, cx - s / 2, cy - s / 4, cx + s / 2, cy + s / 4, 1, color);
        }
    }

    public static void drawRotatedLine(final DrawContext ctx, final int x1, final int y1, final int x2, final int y2,
                                       final int thickness, final int color) {
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        final float angle = (float) Math.atan2(dy, dx);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x1, y1);
        ctx.getMatrices().rotate(angle);
        ctx.fill(0, -thickness / 2, (int) len, thickness - thickness / 2, color);
        ctx.getMatrices().popMatrix();
    }

    public static void drawRectBorder(final DrawContext ctx, final int x0, final int y0, final int x1, final int y1,
                                      final int thickness, final int color) {
        ctx.fill(x0, y0, x1, y0 + thickness, color);
        ctx.fill(x0, y1 - thickness, x1, y1, color);
        ctx.fill(x0, y0, x0 + thickness, y1, color);
        ctx.fill(x1 - thickness, y0, x1, y1, color);
    }

    public static void renderKicker(final DrawContext ctx, final TextRenderer tr, final int width, final int yBase,
                                    final String label, final float now, final int startTick, final int duration) {
        final float t = clamp01((now - startTick) / (float) duration);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 200) & 0xFF;
        final int color = (alpha << 24) | (TEXT_KICKER & 0xFFFFFF);
        final int yOffset = (int) ((1f - ease) * 8);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(spaceLetters(label)), width / 2, yBase + yOffset, color);
    }

    public static void renderFadeOut(final DrawContext ctx, final int width, final int height, final float now,
                                     final int holdEnd, final int duration) {
        if (now <= holdEnd) return;
        final float t = clamp01((now - holdEnd) / (float) duration);
        final int alpha = (int) (t * 255) & 0xFF;
        ctx.fill(0, 0, width, height, (alpha << 24));
    }

    public static int lerpColor(final int a, final int b, final float t) {
        final int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        final int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        final int ra = (int) (aa + (ba - aa) * t);
        final int rr = (int) (ar + (br - ar) * t);
        final int rg = (int) (ag + (bg - ag) * t);
        final int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static int withAlpha(final int color, final float alpha01) {
        final int a = (int) (clamp01(alpha01) * 255) & 0xFF;
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static float clamp01(final float v) {
        return Math.max(0f, Math.min(1f, v));
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

    /** Ambient sparkle layer — tiny dots that fade in and out at random positions. */
    public static final class Sparkles {
        private final Sparkle[] sparkles;
        private final Random rng = new Random(0xCAFE);
        private int globalTick = 0;

        public Sparkles(final int count, final int width, final int height) {
            sparkles = new Sparkle[count];
            for (int i = 0; i < count; i++) sparkles[i] = new Sparkle(rng, width, height, globalTick);
        }

        public void tick(final int width, final int height) {
            globalTick++;
            for (final Sparkle s : sparkles) {
                if (globalTick - s.lifeStart > s.lifeDuration) {
                    s.respawn(rng, width, height, globalTick);
                }
            }
        }

        public void render(final DrawContext ctx, final float partial) {
            for (final Sparkle s : sparkles) {
                final float t = (globalTick - s.lifeStart + partial) / (float) s.lifeDuration;
                if (t < 0 || t > 1) continue;
                final float env = t < 0.5f ? t * 2f : (1f - t) * 2f;
                final int a = (int) (env * 0.45f * 255) & 0xFF;
                if (a == 0) continue;
                final int color = (a << 24) | 0xFFFFFF;
                ctx.fill(s.x, s.y, s.x + s.size, s.y + s.size, color);
            }
        }

        private static final class Sparkle {
            int x, y, size;
            int lifeStart;
            int lifeDuration;

            Sparkle(final Random rng, final int w, final int h, final int now) { respawn(rng, w, h, now); }

            void respawn(final Random rng, final int w, final int h, final int now) {
                this.x = rng.nextInt(Math.max(1, w));
                this.y = rng.nextInt(Math.max(1, h));
                this.size = 2 + rng.nextInt(2);
                this.lifeStart = now;
                this.lifeDuration = 30 + rng.nextInt(50);
            }
        }
    }
}
