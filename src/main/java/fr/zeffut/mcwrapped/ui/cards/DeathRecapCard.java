package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

public final class DeathRecapCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int COUNTER_START = 14;
    private static final int COUNTER_DURATION = 28;
    private static final int CAUSE_START = 36;
    private static final int CAUSE_DURATION = 14;
    private static final int HOLD_END = 120;

    private final WrappedContext context;
    private final long deaths;
    private final List<Map.Entry<String, Long>> topCauses;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public DeathRecapCard(final WrappedContext context) {
        this.context = context;
        this.deaths = context.deaths();
        this.topCauses = context.topKilledBy(1);
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(8, width, height);
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_HURT, 0.7f, 0.6f));
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);
        if (deaths == 0 && ticks == COUNTER_START) {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.5f));
        }
        if (deaths > 0 && ticks == COUNTER_START) {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.ENTITY_GHAST_HURT, 0.8f, 0.4f));
        }
    }

    @Override
    public void render(final DrawContext ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        final boolean perfectStreak = deaths == 0;

        if (perfectStreak) {
            CardEffects.renderGradient(ctx, width, height, CardEffects.BG_TOP, CardEffects.BG_BOTTOM);
        } else {
            CardEffects.renderGradient(ctx, width, height, CardEffects.BG_TOP_RED, CardEffects.BG_BOTTOM_RED);
        }
        sparkles.render(ctx, partial);
        CardEffects.renderHalo(ctx, width / 2, height / 2,
                now, perfectStreak ? CardEffects.ACCENT_GREEN : CardEffects.ACCENT_RED);

        CardEffects.renderKicker(ctx, tr, width, height / 2 - 70,
                perfectStreak ? "PERFECT STREAK" : "DEATHS",
                now, KICKER_START, KICKER_DURATION);

        renderHero(ctx, tr, width, height, now, perfectStreak);
        renderCause(ctx, tr, width, height, now, perfectStreak);

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderHero(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now, final boolean perfect) {
        final float t = CardEffects.clamp01((now - COUNTER_START) / (float) COUNTER_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (CardEffects.clamp01(t * 1.5f) * 255) & 0xFF;

        final String text;
        final int colorBase;
        if (perfect) {
            text = "0";
            colorBase = CardEffects.ACCENT_GREEN;
        } else {
            text = String.valueOf(Math.round(ease * deaths));
            colorBase = CardEffects.TEXT_HERO;
        }

        final float scale = 6f;
        final int textWidth = tr.getWidth(text);
        final int color = (alpha << 24) | (colorBase & 0xFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, text,
                (int) ((width / 2f - textWidth * scale / 2f) / scale),
                (int) ((height / 2f - 5 * scale / 2f) / scale),
                color, true);
        ctx.getMatrices().pop();
    }

    private void renderCause(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now, final boolean perfect) {
        final float t = CardEffects.clamp01((now - CAUSE_START) / (float) CAUSE_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 255) & 0xFF;
        final int yOffset = (int) ((1f - ease) * 8);

        final String label;
        final int color;
        if (perfect) {
            label = "not a single death";
            color = (alpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF);
        } else if (topCauses.isEmpty()) {
            label = "deaths: " + deaths;
            color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
        } else {
            final Map.Entry<String, Long> top = topCauses.get(0);
            final String causeName = entityName(top.getKey());
            final String roast = roast(top.getKey());
            label = "mostly killed by " + causeName + (roast.isEmpty() ? "" : " — " + roast);
            color = (alpha << 24) | (CardEffects.ACCENT_RED & 0xFFFFFF);
        }
        ctx.drawCenteredTextWithShadow(tr, Text.literal(label), width / 2, height / 2 + 80 + yOffset, color);
    }

    private static String entityName(final String id) {
        final String stripped = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return stripped.replace('_', ' ');
    }

    private static String roast(final String id) {
        final String name = entityName(id);
        return switch (name) {
            case "chicken" -> "yes, really";
            case "bat" -> "brutal";
            case "rabbit" -> "ouch";
            case "villager" -> "rude";
            case "sheep" -> "okay then";
            default -> "";
        };
    }
}
