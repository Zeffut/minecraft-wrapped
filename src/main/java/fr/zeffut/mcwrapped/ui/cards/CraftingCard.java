package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.WrappedSounds;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CraftingCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int FIRST_ROW_START = 14;
    private static final int ROW_STAGGER = 10;
    private static final int ROW_DURATION = 14;
    private static final int FOOTER_START = 50;
    private static final int FOOTER_DURATION = 12;
    private static final int HOLD_END = 130;

    private final WrappedContext context;
    private final List<Map.Entry<String, Long>> top;
    private final ItemStack[] icons;
    private final long totalCrafted;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;

    public CraftingCard(final WrappedContext context) {
        this.context = context;
        this.top = context.delta().deltas().getOrDefault("minecraft:crafted", Map.of()).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .toList();
        this.icons = new ItemStack[top.size()];
        for (int i = 0; i < top.size(); i++) icons[i] = stackFor(top.get(i).getKey());
        this.totalCrafted = context.delta().total("minecraft:crafted");
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(14, width, height);
        WrappedSounds.play(client, SoundEvents.BLOCK_SMITHING_TABLE_USE, 1.4f, 0.6f);
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);
        for (int i = 0; i < top.size(); i++) {
            if (ticks == FIRST_ROW_START + i * ROW_STAGGER) {
                WrappedSounds.play(MinecraftClient.getInstance(), SoundEvents.BLOCK_ANVIL_LAND, 1.3f, 0.3f);
            }
        }
    }

    @Override
    public void render(final DrawContext ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        CardEffects.renderGradient(ctx, width, height, CardEffects.bgTop(), CardEffects.bgBottom());
        sparkles.render(ctx, partial);

        CardEffects.renderKicker(ctx, tr, width, 50, "TOP CRAFTED", now, KICKER_START, KICKER_DURATION);

        if (top.isEmpty()) {
            renderEmpty(ctx, tr, width, height, now);
        } else {
            for (int i = 0; i < top.size(); i++) {
                renderRow(ctx, tr, width, height, now, i, top.get(i), icons[i]);
            }
            renderFooter(ctx, tr, width, height, now);
        }

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderEmpty(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - FIRST_ROW_START) / 12f);
        if (t <= 0) return;
        final int alpha = (int) (t * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, Text.literal("nothing crafted this month"), width / 2, height / 2, color);
    }

    private void renderRow(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now,
                           final int index, final Map.Entry<String, Long> entry, final ItemStack icon) {
        final float t = CardEffects.clamp01((now - FIRST_ROW_START - index * ROW_STAGGER) / (float) ROW_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);

        final int rowHeight = 64;
        final int totalRowsHeight = top.size() * rowHeight + (top.size() - 1) * 16;
        final int rowsTop = height / 2 - totalRowsHeight / 2;
        final int yBase = rowsTop + index * (rowHeight + 16);

        final int slideOffset = (int) ((1f - ease) * 80);
        final int alpha = (int) (ease * 255) & 0xFF;

        final int rowX = width / 2 - 240;
        final int x = rowX + slideOffset;

        final int cardBg = (Math.min(alpha, 30) << 24) | 0xFFFFFF;
        ctx.fill(x - 8, yBase - 6, x + 480, yBase + rowHeight - 6, cardBg);

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

        final int iconCx = x + 110;
        final int iconCy = yBase + rowHeight / 2 - 6;
        if (!icon.isEmpty()) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(iconCx, iconCy, 0f);
            ctx.getMatrices().scale(2.4f, 2.4f, 1f);
            ctx.drawItem(icon, -8, -8);
            ctx.getMatrices().pop();
        }

        final int textX = iconCx + 32;
        final Text name = itemName(entry.getKey());
        final float nameScale = 1.7f;
        final int nameColor = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(nameScale, nameScale, 1f);
        ctx.drawText(tr, name,
                (int) (textX / nameScale),
                (int) ((yBase + 14) / nameScale),
                nameColor, true);
        ctx.getMatrices().pop();

        final String count = formatCount(entry.getValue());
        final float countScale = 2.4f;
        final int countWidth = (int) (tr.getWidth(count) * countScale);
        final int countX = width / 2 + 240 - countWidth - 8;
        final int countColor = (alpha << 24) | (CardEffects.accent() & 0xFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(countScale, countScale, 1f);
        ctx.drawText(tr, count,
                (int) (countX / countScale),
                (int) ((yBase + 12) / countScale),
                countColor, true);
        ctx.getMatrices().pop();
    }

    private void renderFooter(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - FOOTER_START) / (float) FOOTER_DURATION);
        if (t <= 0) return;
        final int alpha = (int) (t * 200) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr,
                Text.literal(totalCrafted + " items crafted total"),
                width / 2, height - 60, color);
    }

    private static ItemStack stackFor(final String itemId) {
        final Identifier id = Identifier.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        final var item = Registries.ITEM.get(id);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private static Text itemName(final String id) {
        final String[] parts = id.split(":");
        if (parts.length != 2) return Text.literal(id);
        return Text.translatable("item." + parts[0] + "." + parts[1]);
    }

    private static String formatCount(final long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        return Long.toString(n);
    }
}
