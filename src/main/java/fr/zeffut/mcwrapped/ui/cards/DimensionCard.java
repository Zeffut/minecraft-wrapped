package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.stats.DimensionTracker;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DimensionCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int FIRST_ROW_START = 14;
    private static final int ROW_STAGGER = 10;
    private static final int ROW_DURATION = 16;
    private static final int HOLD_END = 130;

    private final WrappedContext context;
    private final List<Map.Entry<String, Long>> top;
    private final long total;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public DimensionCard(final WrappedContext context) {
        this.context = context;
        if (DimensionTracker.INSTANCE != null) {
            this.top = DimensionTracker.INSTANCE.topForMonth(context.month(), 3);
            this.total = DimensionTracker.INSTANCE.totalTicksInMonth(context.month());
        } else {
            this.top = List.of();
            this.total = 0L;
        }
    }

    public boolean hasData() { return !top.isEmpty(); }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(14, width, height);
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.BLOCK_PORTAL_TRAVEL, 1.5f, 0.3f));
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

        CardEffects.renderKicker(ctx, tr, width, 50, "DIMENSIONS EXPLORED", now, KICKER_START, KICKER_DURATION);

        for (int i = 0; i < top.size(); i++) {
            renderRow(ctx, tr, width, height, now, i, top.get(i));
        }

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderRow(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now,
                           final int index, final Map.Entry<String, Long> entry) {
        final float t = CardEffects.clamp01((now - FIRST_ROW_START - index * ROW_STAGGER) / (float) ROW_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);

        final int rowHeight = 64;
        final int totalRowsHeight = top.size() * rowHeight + (top.size() - 1) * 16;
        final int rowsTop = height / 2 - totalRowsHeight / 2 + 14;
        final int yBase = rowsTop + index * (rowHeight + 16);

        final int slideOffset = (int) ((1f - ease) * 80);
        final int alpha = (int) (ease * 255) & 0xFF;

        final int rowX = width / 2 - 220;
        final int x = rowX + slideOffset;

        // Glass background tinted by dimension.
        final int dimColor = colorForDimension(entry.getKey());
        final int bgAlpha = Math.min(alpha, 35);
        ctx.fill(x - 8, yBase - 6, x + 440, yBase + rowHeight - 6, (bgAlpha << 24) | (dimColor & 0xFFFFFF));

        // Rank.
        final String rank = "#" + (index + 1);
        final float rankScale = 3.4f;
        final int rankColor = (alpha << 24) | (CardEffects.ACCENT_GOLD & 0xFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(rankScale, rankScale, 1f);
        ctx.drawText(tr, rank,
                (int) (x / rankScale),
                (int) ((yBase + 12) / rankScale),
                rankColor, true);
        ctx.getMatrices().pop();

        // Dim color swatch.
        final int swatchX = x + 78;
        final int swatchY = yBase + rowHeight / 2 - 16;
        ctx.fill(swatchX, swatchY, swatchX + 32, swatchY + 32, (alpha << 24) | (dimColor & 0xFFFFFF));

        // Name + percentage.
        final String name = displayDimensionName(entry.getKey());
        final float nameScale = 1.7f;
        final int nameColor = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(nameScale, nameScale, 1f);
        ctx.drawText(tr, name,
                (int) ((swatchX + 48) / nameScale),
                (int) ((yBase + 14) / nameScale),
                nameColor, true);
        ctx.getMatrices().pop();

        final long pct = total > 0 ? Math.round(entry.getValue() * 100.0 / total) : 0;
        final long minutes = entry.getValue() / 20 / 60;
        final String details = pct + "% • " + (minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m");
        ctx.drawTextWithShadow(tr, Text.literal(details),
                swatchX + 48, yBase + rowHeight - 18, (alpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF));
    }

    private static int colorForDimension(final String id) {
        return switch (id) {
            case "minecraft:overworld" -> 0xFF6FB041;
            case "minecraft:the_nether" -> 0xFF6B2A29;
            case "minecraft:the_end" -> 0xFFE0DBA0;
            default -> 0xFF8B82FF;
        };
    }

    private static String displayDimensionName(final String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            default -> id.replace("minecraft:", "").replace('_', ' ').toUpperCase(Locale.ROOT);
        };
    }
}
