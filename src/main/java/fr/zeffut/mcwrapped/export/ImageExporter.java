package fr.zeffut.mcwrapped.export;

import fr.zeffut.mcwrapped.McWrappedClient;
import fr.zeffut.mcwrapped.config.AspectRatio;
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.config.McWrappedConfig;
import fr.zeffut.mcwrapped.stats.WorldKey;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates a 1080x1920 PNG recap that mirrors the in-game FinalCard tile grid:
 * indigo gradient, "YOUR MINECRAFT WRAPPED" + month header, then a 2×4 grid of bordered
 * tiles with color-coded values. Uses the bundled Monocraft pixel font for the Minecraft feel.
 */
public final class ImageExporter {

    // Defaults; resolved per-call from the user's chosen aspect ratio.
    private static final int DEFAULT_W = 1080;
    private static final int DEFAULT_H = 1920;
    // Current output dimensions, swapped each call to render(). Confined to the export thread.
    private static int W = DEFAULT_W;
    private static int H = DEFAULT_H;

    private static final Color BG_TOP = new Color(0x0F, 0x0F, 0x23);
    private static final Color BG_BOTTOM = new Color(0x1E, 0x1B, 0x4B);
    private static final Color ACCENT_GREEN = new Color(0x22, 0xC5, 0x5E);
    private static final Color ACCENT_GOLD = new Color(0xFC, 0xD3, 0x4D);
    private static final Color ACCENT_INDIGO = new Color(0x8B, 0x82, 0xFF);
    private static final Color ACCENT_RED = new Color(0xEF, 0x44, 0x44);
    private static final Color TEXT_HERO = new Color(0xF8, 0xFA, 0xFC);
    private static final Color TEXT_DIM = new Color(0xCB, 0xD5, 0xE1);
    private static final Color TEXT_KICKER = new Color(0x94, 0xA3, 0xB8);
    private static final Color TILE_BG = new Color(255, 255, 255, 16);
    private static final Color TILE_BORDER = new Color(255, 255, 255, 60);

    private static Font baseFont;

    private ImageExporter() {}

    public static BufferedImage render(final WrappedContext ctx) {
        ensureFontLoaded();

        final AspectRatio ar = ConfigManager.get().aspectRatio;
        W = ar.width();
        H = ar.height();
        final BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        try {
            renderBackground(g);
            renderSparkleTexture(g);
            renderHeader(g, ctx);
            renderTileGrid(g, ctx);
            renderFooter(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    public static Path export(final WrappedContext ctx) throws IOException {
        final BufferedImage img = render(ctx);
        final Path out = FabricLoader.getInstance().getGameDir()
                .resolve("screenshots").resolve("wrapped")
                .resolve("wrapped-" + ctx.month().format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".png");
        Files.createDirectories(out.getParent());
        try {
            ImageIO.write(img, "PNG", out.toFile());
        } catch (final IOException e) {
            throw new IOException("Failed to write Wrapped PNG to " + out + " (disk full or read-only filesystem?): " + e.getMessage(), e);
        }
        return out;
    }

    // --- Layers -------------------------------------------------------------

    private static void renderBackground(final Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, H, BG_BOTTOM));
        g.fillRect(0, 0, W, H);
    }

    private static void renderSparkleTexture(final Graphics2D g) {
        final java.util.Random rng = new java.util.Random(0xCAFEBABEL);
        for (int i = 0; i < 200; i++) {
            final int x = rng.nextInt(W);
            final int y = rng.nextInt(H);
            final int size = 4 + rng.nextInt(4);
            final int alpha = 30 + rng.nextInt(70);
            g.setColor(new Color(255, 255, 255, alpha));
            g.fillRect(x, y, size, size);
        }
    }

    private static void renderHeader(final Graphics2D g, final WrappedContext ctx) {
        // Kicker.
        g.setFont(font(48));
        g.setColor(TEXT_KICKER);
        drawCentered(g, "YOUR MINECRAFT WRAPPED", W / 2, 180);

        // Month + year on one line, big.
        final String monthName = ctx.month().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        final String header = monthName + " " + ctx.month().getYear();
        g.setFont(font(140));
        g.setColor(TEXT_HERO);
        drawCentered(g, header, W / 2, 320);

        // Underline.
        g.setColor(ACCENT_GREEN);
        g.fillRect(W / 2 - 110, 360, 220, 6);
    }

    private static void renderTileGrid(final Graphics2D g, final WrappedContext ctx) {
        final List<Tile> tiles = buildTiles(ctx);
        final int cols = 2;
        final int rows = (tiles.size() + cols - 1) / cols;
        final int gridLeft = 80;
        final int gridRight = W - 80;
        final int gridTop = 460;
        final int gridBottom = H - 200;
        final int gap = 24;

        final int tileW = (gridRight - gridLeft - (cols - 1) * gap) / cols;
        final int tileH = (gridBottom - gridTop - (rows - 1) * gap) / rows;

        for (int i = 0; i < tiles.size(); i++) {
            final int col = i % cols;
            final int row = i / cols;
            final int x = gridLeft + col * (tileW + gap);
            final int y = gridTop + row * (tileH + gap);
            renderTile(g, x, y, tileW, tileH, tiles.get(i));
        }
    }

    private static void renderTile(final Graphics2D g, final int x, final int y, final int w, final int h, final Tile tile) {
        // Glass background.
        g.setColor(TILE_BG);
        g.fillRoundRect(x, y, w, h, 18, 18);
        // Border.
        g.setColor(TILE_BORDER);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(x, y, w, h, 18, 18);

        // Label.
        g.setFont(font(34));
        g.setColor(TEXT_KICKER);
        g.drawString(tile.label, x + 36, y + 60);

        // Value with auto-shrink.
        int size = 110;
        Font f = font(size);
        g.setFont(f);
        while (size > 36 && g.getFontMetrics().stringWidth(tile.value) > w - 72) {
            size -= 6;
            f = font(size);
            g.setFont(f);
        }
        g.setColor(tile.color);
        g.drawString(tile.value, x + 36, y + h - 50);
    }

    private static void renderFooter(final Graphics2D g) {
        final McWrappedConfig cfg = ConfigManager.get();
        g.setFont(font(32));
        g.setColor(TEXT_KICKER);
        drawCentered(g, "MINECRAFT WRAPPED", W / 2, H - 110);
        g.setFont(font(22));
        g.setColor(TEXT_DIM);
        drawCentered(g, "github.com/Zeffut/minecraft-wrapped", W / 2, H - 70);

        // IGN watermark (top-left), if enabled and not masked.
        if (cfg.watermarkSkinHead && !cfg.maskIgn) {
            final String ign = currentIgn();
            if (!ign.isEmpty()) {
                // Tiny "skin" tile: a colored square hashed from the IGN, then "by <IGN>" beside it.
                final int tileSize = 48;
                final int tileX = 36;
                final int tileY = 36;
                final int hue = ign.hashCode() & 0xFFFFFF;
                g.setColor(new Color((hue >> 16) & 0xFF | 0x40, (hue >> 8) & 0xFF | 0x40, hue & 0xFF | 0x40));
                g.fillRect(tileX, tileY, tileSize, tileSize);
                g.setColor(TILE_BORDER);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(tileX, tileY, tileSize, tileSize);
                g.setFont(font(24));
                g.setColor(TEXT_DIM);
                g.drawString("by " + ign, tileX + tileSize + 12, tileY + 32);
            }
        }

        // User signature line, if set.
        if (cfg.signature != null && !cfg.signature.isBlank()) {
            g.setFont(font(20));
            g.setColor(TEXT_DIM);
            drawCentered(g, cfg.signature, W / 2, H - 36);
        }
    }

    private static String currentIgn() {
        try {
            final MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getSession() != null ? mc.getSession().getUsername() : "";
        } catch (final RuntimeException e) {
            return "";
        }
    }

    // --- Tile data ----------------------------------------------------------

    private static List<Tile> buildTiles(final WrappedContext ctx) {
        final long minutes = ctx.playTimeTicks() / 20 / 60;
        final String time = minutes >= 60
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

        final long sharePct = ctx.playTimeTicks() > 0
                ? Math.round(ctx.serverTicks() * 100.0 / Math.max(ctx.playTimeTicks(), 1L))
                : 0;

        return List.of(
                new Tile("PLAY TIME", time, ACCENT_GREEN),
                new Tile("PLAYERS MET", String.valueOf(ctx.playersMet()), ACCENT_GOLD),
                new Tile("TOP WORLD", topWorld, TEXT_HERO),
                new Tile("ON SERVERS", sharePct + "%", ACCENT_INDIGO),
                new Tile("TOP MOB", topMob, TEXT_HERO),
                new Tile("TOP BLOCK", topBlock, TEXT_HERO),
                new Tile("DEATHS", String.valueOf(ctx.deaths()), ACCENT_RED),
                new Tile("MESSAGES", String.valueOf(ctx.messagesSent()), ACCENT_GREEN)
        );
    }

    // --- Font + helpers -----------------------------------------------------

    private static synchronized void ensureFontLoaded() {
        if (baseFont != null) return;
        try (final InputStream in = ImageExporter.class.getResourceAsStream("/assets/mcwrapped/font/Monocraft.ttf")) {
            if (in == null) {
                McWrappedClient.LOGGER.warn("Monocraft font not bundled; falling back to monospace.");
                baseFont = new Font(Font.MONOSPACED, Font.BOLD, 12);
                return;
            }
            baseFont = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (final IOException | FontFormatException e) {
            McWrappedClient.LOGGER.warn("Failed to load Monocraft: {}", e.getMessage());
            baseFont = new Font(Font.MONOSPACED, Font.BOLD, 12);
        }
    }

    private static Font font(final float size) {
        return baseFont.deriveFont(size);
    }

    private static void drawCentered(final Graphics2D g, final String text, final int cx, final int y) {
        final int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, cx - w / 2, y);
    }

    private static String stripNs(final String id) {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1).replace('_', ' ') : id;
    }

    private static String compactNum(final long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    private record Tile(String label, String value, Color color) {}
}
