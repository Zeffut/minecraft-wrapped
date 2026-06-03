package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.ui.WrappedSounds;
import fr.zeffut.mcwrapped.McWrappedClient;
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.export.ClipboardHelper;
import fr.zeffut.mcwrapped.export.ImageExporter;
import fr.zeffut.mcwrapped.stats.WorldKey;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import fr.zeffut.mcwrapped.ui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FinalCard implements Card {

    private static final int TITLE_START = 4;
    private static final int TITLE_DURATION = 10;
    private static final int FIRST_TILE_START = 14;
    private static final int TILE_STAGGER = 4;
    private static final int TILE_DURATION = 12;
    private static final int BUTTONS_START = 40;
    private static final int BUTTONS_DURATION = 10;

    private static final int TOAST_DURATION_TICKS = 100;

    private final WrappedContext context;
    private final List<Tile> tiles;
    private CardEffects.Sparkles sparkles;
    private int ticks = 0;
    private boolean started = false;
    private boolean closeRequested = false;

    // Button rects (recomputed each render in case window resizes).
    private int saveX, saveY, saveW, saveH;
    private int copyX, copyY, copyW, copyH;
    private int closeX, closeY, closeW, closeH;

    private String toastMessage;
    private int toastStartTick = -1;

    public FinalCard(final WrappedContext context) {
        this.context = context;
        this.tiles = buildTiles(context);
    }

    @Override
    public void start(final MinecraftClient client, final int width, final int height) {
        sparkles = new CardEffects.Sparkles(22, width, height);
        WrappedSounds.play(client, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.6f);
        started = true;
    }

    @Override
    public void tick(final int width, final int height) {
        if (!started) return;
        ticks++;
        sparkles.tick(width, height);
        if (toastStartTick >= 0 && ticks - toastStartTick > TOAST_DURATION_TICKS) {
            toastStartTick = -1;
            toastMessage = null;
        }
    }

    @Override
    public void render(final DrawContext ctx, final int width, final int height, final int mouseX, final int mouseY, final float partial) {
        final float now = ticks + partial;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        CardEffects.renderGradient(ctx, width, height, CardEffects.bgTop(), CardEffects.bgBottom());
        sparkles.render(ctx, partial);

        renderTitle(ctx, tr, width, height, now);
        renderTiles(ctx, tr, width, height, now);
        renderButtons(ctx, tr, width, height, now, mouseX, mouseY);
        renderToast(ctx, tr, width, height, now);
    }

    @Override
    public boolean isDone() {
        return closeRequested;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button != 0 || ticks < BUTTONS_START + BUTTONS_DURATION / 2) return false;
        if (inRect(mouseX, mouseY, saveX, saveY, saveW, saveH)) {
            triggerSave();
            return true;
        }
        if (inRect(mouseX, mouseY, copyX, copyY, copyW, copyH)) {
            triggerCopy();
            return true;
        }
        if (inRect(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            closeRequested = true;
            return true;
        }
        return false;
    }

    // --- Render layers ------------------------------------------------------

    private void renderTitle(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final float t = CardEffects.clamp01((now - TITLE_START) / (float) TITLE_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 255) & 0xFF;
        final int yOffset = (int) ((1f - ease) * 8);
        final int yKicker = height / 2 - 170 + yOffset;

        // Kicker.
        final int kickerColor = (alpha << 24) | (CardEffects.TEXT_KICKER & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr,
                Text.literal("Y O U R   M I N E C R A F T   W R A P P E D"),
                width / 2, yKicker, kickerColor);

        // Big month + year.
        final String monthLabel = context.month().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT)
                + " " + context.month().getYear();
        final float scale = 2.6f;
        final int monthWidth = (int) (tr.getWidth(monthLabel) * scale);
        final int monthX = width / 2 - monthWidth / 2;
        final int monthY = yKicker + 16;
        final int monthColor = (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, monthLabel, (int) (monthX / scale), (int) (monthY / scale), monthColor, true);
        ctx.getMatrices().pop();
    }

    private void renderTiles(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        final int cols = 2;
        final int rows = (tiles.size() + cols - 1) / cols;
        final int tileW = 200;
        final int tileH = 48;
        final int gap = 8;
        final int gridW = cols * tileW + (cols - 1) * gap;
        final int gridH = rows * tileH + (rows - 1) * gap;
        final int gridX = width / 2 - gridW / 2;
        final int gridY = height / 2 - gridH / 2 + 28;

        for (int i = 0; i < tiles.size(); i++) {
            final float t = CardEffects.clamp01((now - FIRST_TILE_START - i * TILE_STAGGER) / (float) TILE_DURATION);
            if (t <= 0) continue;
            final float ease = Easing.EASE_OUT_CUBIC.apply(t);
            final int alpha = (int) (ease * 255) & 0xFF;

            final int col = i % cols;
            final int row = i / cols;
            final int tx = gridX + col * (tileW + gap);
            final int ty = gridY + row * (tileH + gap) + (int) ((1f - ease) * 12);

            renderTile(ctx, tr, tx, ty, tileW, tileH, alpha, tiles.get(i));
        }
    }

    private void renderTile(final DrawContext ctx, final TextRenderer tr, final int x, final int y, final int w, final int h,
                            final int alpha, final Tile tile) {
        // Glass bg.
        final int bgColor = (Math.min(alpha, 28) << 24) | 0xFFFFFF;
        ctx.fill(x, y, x + w, y + h, bgColor);
        // Subtle border.
        final int borderAlpha = Math.min(alpha, 50);
        CardEffects.drawRectBorder(ctx, x, y, x + w, y + h, 1, (borderAlpha << 24) | 0xFFFFFF);

        // Label.
        final int labelColor = (alpha << 24) | (CardEffects.TEXT_KICKER & 0xFFFFFF);
        ctx.drawTextWithShadow(tr, Text.literal(tile.label), x + 10, y + 8, labelColor);

        // Value (auto-shrink scale if too wide).
        float vScale = 1.6f;
        int vw = (int) (tr.getWidth(tile.value) * vScale);
        while (vw > w - 20 && vScale > 0.9f) {
            vScale -= 0.1f;
            vw = (int) (tr.getWidth(tile.value) * vScale);
        }
        final int valueColor = (alpha << 24) | (tile.color & 0xFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(vScale, vScale, 1f);
        ctx.drawText(tr, tile.value,
                (int) ((x + 10) / vScale),
                (int) ((y + 24) / vScale),
                valueColor, true);
        ctx.getMatrices().pop();
    }

    private void renderButtons(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now,
                               final int mouseX, final int mouseY) {
        final float t = CardEffects.clamp01((now - BUTTONS_START) / (float) BUTTONS_DURATION);
        if (t <= 0) return;
        final float ease = Easing.EASE_OUT_CUBIC.apply(t);
        final int alpha = (int) (ease * 255) & 0xFF;

        final int btnW = 110;
        final int btnH = 26;
        final int gap = 12;
        final int totalW = btnW * 3 + gap * 2;

        saveW = btnW; saveH = btnH;
        copyW = btnW; copyH = btnH;
        closeW = btnW; closeH = btnH;
        saveX = width / 2 - totalW / 2;
        saveY = height / 2 + 150;
        copyX = saveX + btnW + gap;
        copyY = saveY;
        closeX = copyX + btnW + gap;
        closeY = saveY;

        final boolean saveHover = inRect(mouseX, mouseY, saveX, saveY, saveW, saveH);
        final boolean copyHover = inRect(mouseX, mouseY, copyX, copyY, copyW, copyH);
        final boolean closeHover = inRect(mouseX, mouseY, closeX, closeY, closeW, closeH);

        // Save button — accent green.
        final int saveBg = saveHover ? 0xFF2EE36C : CardEffects.accent();
        ctx.fill(saveX, saveY, saveX + saveW, saveY + saveH, withAlpha(saveBg, alpha));
        CardEffects.drawRectBorder(ctx, saveX, saveY, saveX + saveW, saveY + saveH, 1, withAlpha(0x16A34A, alpha));
        ctx.drawCenteredTextWithShadow(tr, Text.literal("Save Image"),
                saveX + saveW / 2, saveY + 9, (alpha << 24) | 0x0F172A);

        // Copy button — accent gold.
        final int copyBg = copyHover ? 0xFFFEDF8E : CardEffects.ACCENT_GOLD;
        ctx.fill(copyX, copyY, copyX + copyW, copyY + copyH, withAlpha(copyBg, alpha));
        CardEffects.drawRectBorder(ctx, copyX, copyY, copyX + copyW, copyY + copyH, 1, withAlpha(0xCA8A04, alpha));
        ctx.drawCenteredTextWithShadow(tr, Text.literal("Copy"),
                copyX + copyW / 2, copyY + 9, (alpha << 24) | 0x0F172A);

        // Close button — neutral.
        final int closeBg = closeHover ? 0xFF334155 : 0xFF1E293B;
        ctx.fill(closeX, closeY, closeX + closeW, closeY + closeH, withAlpha(closeBg, alpha));
        CardEffects.drawRectBorder(ctx, closeX, closeY, closeX + closeW, closeY + closeH, 1, withAlpha(0x475569, alpha));
        ctx.drawCenteredTextWithShadow(tr, Text.literal("Close"),
                closeX + closeW / 2, closeY + 9, (alpha << 24) | (CardEffects.TEXT_HERO & 0xFFFFFF));
    }

    private void renderToast(final DrawContext ctx, final TextRenderer tr, final int width, final int height, final float now) {
        if (toastMessage == null || toastStartTick < 0) return;
        final float age = now - toastStartTick;
        final float lifetime = TOAST_DURATION_TICKS;
        final float fadeIn = Math.min(1f, age / 6f);
        final float fadeOut = Math.min(1f, (lifetime - age) / 12f);
        final float alphaF = Math.max(0f, Math.min(fadeIn, fadeOut));
        final int alpha = (int) (alphaF * 230) & 0xFF;
        if (alpha == 0) return;

        final int color = (alpha << 24) | (CardEffects.ACCENT_GOLD & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(toastMessage), width / 2, height - 24, color);
    }

    // --- Logic --------------------------------------------------------------

    private void triggerCopy() {
        toastMessage = "Copying...";
        toastStartTick = ticks;
        new Thread(() -> {
            try {
                ClipboardHelper.copyImage(ImageExporter.render(context));
                MinecraftClient.getInstance().execute(() -> {
                    toastMessage = "Copied to clipboard!";
                    toastStartTick = ticks;
                    Telemetry.capture(Events.CLIPBOARD_COPIED, Map.of("success", true));
                    WrappedSounds.play(MinecraftClient.getInstance(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 0.4f);
                });
            } catch (final IOException | RuntimeException e) {
                McWrappedClient.LOGGER.warn("Clipboard copy failed", e);
                Telemetry.capture(Events.CLIPBOARD_COPIED, Map.of("success", false));
                MinecraftClient.getInstance().execute(() -> {
                    toastMessage = "Copy failed: " + e.getMessage();
                    toastStartTick = ticks;
                });
            }
        }, "mcwrapped-copy").start();
    }

    private void triggerSave() {
        toastMessage = "Saving...";
        toastStartTick = ticks;
        new Thread(() -> {
            try {
                final Path file = ImageExporter.export(context);
                final MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    toastMessage = "Saved to screenshots/wrapped/" + file.getFileName();
                    toastStartTick = ticks;
                    Telemetry.capture(Events.IMAGE_SAVED, Map.of(
                            "success", true,
                            "aspect_ratio", ConfigManager.get().aspectRatio.name()));
                    WrappedSounds.play(client, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f, 0.4f);
                });
            } catch (final IOException | RuntimeException e) {
                McWrappedClient.LOGGER.warn("Image export failed", e);
                Telemetry.capture(Events.IMAGE_SAVED, Map.of(
                        "success", false,
                        "aspect_ratio", ConfigManager.get().aspectRatio.name()));
                Telemetry.capture(Events.EXPORT_FAILED, Map.of(
                        "stage", "save",
                        "reason", e.getClass().getSimpleName()));
                MinecraftClient.getInstance().execute(() -> {
                    toastMessage = "Export failed: " + e.getMessage();
                    toastStartTick = ticks;
                });
            }
        }, "mcwrapped-export").start();
    }

    private static boolean inRect(final double x, final double y, final int rx, final int ry, final int rw, final int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private static int withAlpha(final int rgb, final int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static List<Tile> buildTiles(final WrappedContext ctx) {
        final long minutes = ctx.playTimeTicks() / 20 / 60;
        final String time = (minutes >= 60)
                ? (minutes / 60) + "h " + String.format(Locale.ROOT, "%02d", minutes % 60) + "m"
                : minutes + "m";

        final String topWorld = ctx.topWorlds(1).stream().findFirst()
                .map(e -> WorldKey.displayNameMasked(e.getKey(), 1))
                .orElse("—");
        final String topMob = ctx.topKilled(1).stream().findFirst()
                .map(e -> stripNs(e.getKey()) + " ×" + e.getValue())
                .orElse("none");
        final String topBlock = ctx.topMined(1).stream().findFirst()
                .map(e -> stripNs(e.getKey()) + " ×" + compactNum(e.getValue()))
                .orElse("none");

        final long sharePct = (ctx.playTimeTicks() > 0)
                ? Math.round(ctx.serverTicks() * 100.0 / Math.max(ctx.playTimeTicks(), 1L))
                : 0;

        return List.of(
                new Tile("PLAY TIME", time, CardEffects.accent()),
                new Tile("PLAYERS MET", String.valueOf(ctx.playersMet()), CardEffects.ACCENT_GOLD),
                new Tile("TOP WORLD", topWorld, CardEffects.TEXT_HERO),
                new Tile("ON SERVERS", sharePct + "%", CardEffects.accentSecondary()),
                new Tile("TOP MOB", topMob, CardEffects.TEXT_HERO),
                new Tile("TOP BLOCK", topBlock, CardEffects.TEXT_HERO),
                new Tile("DEATHS", String.valueOf(ctx.deaths()), CardEffects.ACCENT_RED),
                new Tile("MESSAGES", String.valueOf(ctx.messagesSent()), CardEffects.accent())
        );
    }

    private static String stripNs(final String id) {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1).replace('_', ' ') : id;
    }

    private static String compactNum(final long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    private record Tile(String label, String value, int color) {}
}
