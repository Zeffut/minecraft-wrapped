package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class SocialCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int COUNTER_START = 14;
    private static final int COUNTER_DURATION = 32;
    private static final int LABEL_START = 22;
    private static final int LABEL_DURATION = 12;
    private static final int BAR_START = 32;
    private static final int BAR_DURATION = 22;
    private static final int FOOTER_START = 46;
    private static final int FOOTER_DURATION = 12;
    private static final int HOLD_END = 130;

    private final WrappedContext context;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public SocialCard(final WrappedContext context) {
        this.context = context;
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(20, width, height);
        client.getSoundManager().play(
                PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.4f, 0.5f));
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
        renderAvatarOrbits(ctx, width, height, now);

        CardEffects.renderKicker(ctx, tr, width, height / 2 - 110, "SOCIAL", now, KICKER_START, KICKER_DURATION);

        renderHeroNumber(ctx, tr, width, height, now);
        renderHeroLabel(ctx, tr, width, height, now);
        renderSplitBar(ctx, tr, width, height, now);
        renderFooter(ctx, tr, width, height, now);

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderAvatarOrbits(final DrawContext ctx, final int width, final int height, final float now) {
        // Concentric ring of small "head" dots orbiting slowly behind the hero number — symbolizes other players.
        final float t = CardEffects.clamp01((now - KICKER_START) / 30f);
        if (t <= 0) return;
        final int alpha = (int) (t * 70) & 0xFF;
        if (alpha == 0) return;
        final int cx = width / 2;
        final int cy = height / 2 - 18;

        for (int ring = 0; ring < 2; ring++) {
            final int radius = 130 + ring * 40;
            final int count = 12 + ring * 4;
            final float baseAngle = now * (0.012f + ring * 0.005f) + ring * 0.4f;
            for (int i = 0; i < count; i++) {
                final float angle = baseAngle + i * (float) (Math.PI * 2 / count);
                final int dx = (int) (Math.cos(angle) * radius);
                final int dy = (int) (Math.sin(angle) * radius * 0.55f); // squashed for stage feel
                final int x = cx + dx;
                final int y = cy + dy;
                final int dotAlpha = Math.min(alpha, 90 - ring * 30);
                final int color = (dotAlpha << 24) | ((ring == 0 ? CardEffects.ACCENT_INDIGO : CardEffects.TEXT_KICKER) & 0xFFFFFF);
                ctx.fill(x - 2, y - 2, x + 3, y + 3, color);
            }
        }
    }

    private void renderHeroNumber(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - COUNTER_START) / (float) COUNTER_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int target = context.playersMet();
        final int shown = Math.round(ease * target);

        final String text = String.valueOf(shown);
        final float scale = 5.4f;
        final int textWidth = tr.getWidth(text);
        final int alpha = (int) (CardEffects.clamp01(t * 1.5f) * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, text,
                (int) ((width / 2f - textWidth * scale / 2f) / scale),
                (int) ((height / 2f - 30 - 5 * scale / 2f) / scale),
                color, true);
        ctx.getMatrices().popMatrix();
    }

    private void renderHeroLabel(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - LABEL_START) / (float) LABEL_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 230) & 0xFF;
        final int yOffset = (int) ((1f - ease) * 6);

        final String label = context.playersMet() == 1 ? "PLAYER MET" : "PLAYERS MET";
        final int color = (alpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(spaceLetters(label)), width / 2, height / 2 + 22 + yOffset, color);
    }

    private void renderSplitBar(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - BAR_START) / (float) BAR_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);

        final long solo = context.soloTicks();
        final long server = context.serverTicks();
        final long total = solo + server;
        final float soloPct = total > 0 ? solo / (float) total : 0f;

        final int barWidth = 380;
        final int barX = width / 2 - barWidth / 2;
        final int barY = height / 2 + 60;
        final int barH = 10;

        // Track.
        final int trackAlpha = (int) (ease * 90) & 0xFF;
        ctx.fill(barX, barY, barX + barWidth, barY + barH, (trackAlpha << 24) | 0x44556B);

        // Solo (green) on left.
        final int soloAlpha = (int) (ease * 255) & 0xFF;
        final int soloFill = (int) (barWidth * soloPct * ease);
        ctx.fill(barX, barY, barX + soloFill, barY + barH, (soloAlpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF));

        // Server (indigo) on right.
        final int serverFill = (int) (barWidth * (1 - soloPct) * ease);
        ctx.fill(barX + barWidth - serverFill, barY, barX + barWidth, barY + barH, (soloAlpha << 24) | (CardEffects.ACCENT_INDIGO & 0xFFFFFF));

        // Labels under each segment.
        final int labelAlpha = (int) (ease * 200) & 0xFF;
        if (soloPct > 0.05f) {
            final String soloLabel = Math.round(soloPct * 100) + "% SOLO";
            ctx.drawTextWithShadow(tr, Text.literal(soloLabel), barX, barY + barH + 6, (labelAlpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF));
        }
        if (1 - soloPct > 0.05f) {
            final String srvLabel = Math.round((1 - soloPct) * 100) + "% SERVERS";
            final int w = tr.getWidth(srvLabel);
            ctx.drawTextWithShadow(tr, Text.literal(srvLabel), barX + barWidth - w, barY + barH + 6, (labelAlpha << 24) | (CardEffects.ACCENT_INDIGO & 0xFFFFFF));
        }
    }

    private void renderFooter(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - FOOTER_START) / (float) FOOTER_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 230) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);

        final long messages = context.messagesSent();
        final int servers = context.serversVisited();
        final StringBuilder sb = new StringBuilder();
        if (messages > 0) sb.append(messages).append(messages == 1 ? " message" : " messages");
        if (servers > 0) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("across ").append(servers).append(servers == 1 ? " server" : " servers");
        }
        if (sb.length() == 0) return;
        ctx.drawCenteredTextWithShadow(tr, Text.literal(sb.toString()), width / 2, height / 2 + 110, color);
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
}
