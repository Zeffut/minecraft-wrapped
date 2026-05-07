package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DistanceCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int COUNTER_START = 12;
    private static final int COUNTER_DURATION = 28;
    private static final int FIRST_BAR_START = 22;
    private static final int BAR_STAGGER = 8;
    private static final int BAR_DURATION = 18;
    private static final int HOLD_END = 130;

    private final WrappedContext context;
    private final List<Mode> modes;
    private final long totalCm;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public DistanceCard(final WrappedContext context) {
        this.context = context;
        final Map<String, Long> custom = context.delta().deltas().getOrDefault("minecraft:custom", Map.of());
        final List<Mode> all = new ArrayList<>();
        all.add(new Mode("ON FOOT", custom.getOrDefault("minecraft:walk_one_cm", 0L)
                + custom.getOrDefault("minecraft:sprint_one_cm", 0L)
                + custom.getOrDefault("minecraft:crouch_one_cm", 0L), CardEffects.ACCENT_GREEN));
        all.add(new Mode("BY ELYTRA", custom.getOrDefault("minecraft:aviate_one_cm", 0L), CardEffects.ACCENT_GOLD));
        all.add(new Mode("BY BOAT", custom.getOrDefault("minecraft:boat_one_cm", 0L), CardEffects.ACCENT_INDIGO));
        all.add(new Mode("ON HORSE", custom.getOrDefault("minecraft:horse_one_cm", 0L), 0xFFB45309));
        all.add(new Mode("SWIMMING", custom.getOrDefault("minecraft:swim_one_cm", 0L)
                + custom.getOrDefault("minecraft:walk_on_water_one_cm", 0L)
                + custom.getOrDefault("minecraft:walk_under_water_one_cm", 0L), 0xFF38BDF8));
        all.add(new Mode("FLYING", custom.getOrDefault("minecraft:fly_one_cm", 0L), 0xFFA78BFA));

        this.modes = all.stream()
                .filter(m -> m.cm > 0)
                .sorted(Comparator.<Mode>comparingLong(m -> m.cm).reversed())
                .limit(4)
                .toList();
        this.totalCm = this.modes.stream().mapToLong(m -> m.cm).sum();
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(16, width, height);
        client.getSoundManager().play(
                PositionedSoundInstance.ui(SoundEvents.ENTITY_HORSE_GALLOP, 1.0f, 0.4f));
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

        CardEffects.renderKicker(ctx, tr, width, height / 2 - 130, "DISTANCE COVERED", now, KICKER_START, KICKER_DURATION);

        renderHero(ctx, tr, width, height, now);
        renderBars(ctx, tr, width, height, now);

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
        final long shown = Math.round(ease * totalCm);

        final String text = formatDistance(shown);
        final float scale = 4.6f;
        final int textWidth = tr.getWidth(text);
        final int alpha = (int) (CardEffects.clamp01(t * 1.5f) * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, text,
                (int) ((width / 2f - textWidth * scale / 2f) / scale),
                (int) ((height / 2f - 60 - 5 * scale / 2f) / scale),
                color, true);
        ctx.getMatrices().popMatrix();
    }

    private void renderBars(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        if (modes.isEmpty()) return;
        final long max = modes.get(0).cm;

        final int barAreaW = 460;
        final int barH = 14;
        final int gap = 10;
        final int rowH = 26;
        final int totalH = modes.size() * (barH + gap) + (modes.size() - 1) * (rowH - barH - gap);
        final int barX = width / 2 - barAreaW / 2;
        final int yBase = height / 2 + 10;

        for (int i = 0; i < modes.size(); i++) {
            final Mode m = modes.get(i);
            final float bt = CardEffects.clamp01((now - FIRST_BAR_START - i * BAR_STAGGER) / (float) BAR_DURATION);
            if (bt <= 0) continue;
            final float ease = Easing.EASE_OUT_CUBIC.apply(bt);
            final int alpha = (int) (ease * 255) & 0xFF;

            final int rowY = yBase + i * (barH + gap + 12);

            // Label.
            final int labelColor = (alpha << 24) | (CardEffects.TEXT_KICKER & 0xFFFFFF);
            ctx.drawTextWithShadow(tr, Text.literal(m.label), barX, rowY, labelColor);

            // Track.
            final int trackY = rowY + 12;
            ctx.fill(barX, trackY, barX + barAreaW, trackY + barH, 0x4044556B);
            // Filled portion.
            final float widthRatio = max > 0 ? (m.cm / (float) max) : 0f;
            final int fillW = (int) (barAreaW * widthRatio * ease);
            ctx.fill(barX, trackY, barX + fillW, trackY + barH, withAlpha(m.color, alpha));

            // Value (right-aligned at end of track).
            final String value = formatDistance(m.cm);
            final int vw = tr.getWidth(value);
            ctx.drawTextWithShadow(tr, Text.literal(value), barX + barAreaW - vw, rowY, (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF));
        }
    }

    private static int withAlpha(final int rgb, final int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static String formatDistance(final long cm) {
        if (cm >= 100_000) {
            final double km = cm / 100_000.0;
            return String.format(Locale.ROOT, "%.2f km", km);
        }
        return (cm / 100) + " m";
    }

    private record Mode(String label, long cm, int color) {}
}
