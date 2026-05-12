package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

public final class TopMobCard implements Card {

    private static final int KICKER_START = 4;
    private static final int KICKER_DURATION = 10;
    private static final int EGG_START = 14;
    private static final int EGG_DURATION = 18;
    private static final int LABEL_START = 28;
    private static final int LABEL_DURATION = 14;
    private static final int HOLD_END = 130;

    private final WrappedContext context;
    private final Optional<Map.Entry<String, Long>> top;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;
    private ItemStack eggStack;

    public TopMobCard(final WrappedContext context) {
        this.context = context;
        final var topList = context.topKilled(1);
        this.top = topList.isEmpty() ? Optional.empty() : Optional.of(topList.get(0));
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(16, width, height);
        eggStack = top.map(e -> spawnEggFor(e.getKey())).orElse(ItemStack.EMPTY);
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ZOMBIE_AMBIENT, 0.6f, 0.5f));
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);
        if (ticks == EGG_START) {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f));
        }
    }

    @Override
    public void render(final DrawContext ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        CardEffects.renderGradient(ctx, width, height, CardEffects.BG_TOP, CardEffects.BG_BOTTOM);
        sparkles.render(ctx, partial);
        CardEffects.renderHalo(ctx, width / 2, height / 2 - 20, now, CardEffects.ACCENT_INDIGO);

        CardEffects.renderKicker(ctx, tr, width, 60, "TOP ENEMY", now, KICKER_START, KICKER_DURATION);

        if (top.isEmpty()) {
            renderEmpty(ctx, tr, width, height, now);
        } else {
            renderEgg(ctx, width, height, now);
            renderLabel(ctx, tr, width, height, now, top.get());
        }

        CardEffects.renderFadeOut(ctx, width, height, now, HOLD_END, 18);
    }

    @Override
    public boolean isDone() {
        return ticks >= HOLD_END + 18;
    }

    private void renderEmpty(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - LABEL_START) / 12f);
        if (t <= 0) return;
        final int alpha = (int) (t * 255) & 0xFF;
        final int color = (alpha << 24) | (CardEffects.TEXT_DIM & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, Text.literal("a peaceful month"), width / 2, height / 2, color);
    }

    private void renderEgg(final DrawContext ctx, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - EGG_START) / (float) EGG_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_BOUNCE.apply(t);
        final float scale = 6f * ease;
        if (scale < 0.05f) return;

        final int cx = width / 2;
        final int cy = height / 2 - 30;
        final float rotation = now * 0.04f;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().rotate(rotation);
        ctx.getMatrices().scale(scale, scale);
        // Item is 16x16 — center on origin.
        ctx.drawItem(eggStack, -8, -8);
        ctx.getMatrices().popMatrix();
    }

    private void renderLabel(final DrawContext ctx, final TextRenderer tr, final int width, final int height,
                             final float now, final Map.Entry<String, Long> entry) {
        final float t = CardEffects.clamp01((now - LABEL_START) / (float) LABEL_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 255) & 0xFF;
        final int yOffset = (int) ((1f - ease) * 12);

        // Mob name.
        final Text name = entityName(entry.getKey());
        final float nameScale = 2.4f;
        final int nameWidth = (int) (tr.getWidth(name) * nameScale);
        final int nameX = width / 2 - nameWidth / 2;
        final int nameY = height / 2 + 70 + yOffset;
        final int nameColor = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(nameScale, nameScale);
        ctx.drawText(tr, name, (int) (nameX / nameScale), (int) (nameY / nameScale), nameColor, true);
        ctx.getMatrices().popMatrix();

        // Count.
        final String count = entry.getValue() + " killed";
        final int countColor = (alpha << 24) | (CardEffects.ACCENT_GREEN & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(count), width / 2, nameY + 30, countColor);
    }

    private static ItemStack spawnEggFor(final String entityId) {
        final Identifier id = Identifier.tryParse(entityId);
        if (id == null) return ItemStack.EMPTY;
        final EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        if (type == null) return ItemStack.EMPTY;
        final SpawnEggItem egg = SpawnEggItem.forEntity(type);
        return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
    }

    private static Text entityName(final String id) {
        final String[] parts = id.split(":");
        if (parts.length != 2) return Text.literal(id);
        return Text.translatable("entity." + parts[0] + "." + parts[1]);
    }
}
