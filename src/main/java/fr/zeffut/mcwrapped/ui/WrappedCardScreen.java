package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.config.TransitionStyle;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import fr.zeffut.mcwrapped.ui.cards.Card;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hosts a sequence of {@link Card}s and crossfades between them.
 *
 * <p>All cards are designed against a fixed {@value #DESIGN_W}×{@value #DESIGN_H} virtual viewport.
 * The screen scales the viewport to fit the actual window so layouts stay consistent at every size.
 */
public final class WrappedCardScreen extends Screen {

    private static final int TRANSITION_HALF = 6;
    private static final float DESIGN_W = 854f;
    private static final float DESIGN_H = 480f;

    @Nullable
    private final Screen parent;
    private final List<Card> cards;

    private int currentIndex = 0;
    private boolean currentStarted = false;
    private int transitionTicks = -1;
    private boolean paused = false;
    /** Sub-tick accumulator so non-integer speed multipliers (0.5x, 1.5x) keep working. */
    private float speedAccumulator = 0f;

    private long cardStartMillis = 0L;
    private long fpsSampleSum = 0L;
    private long fpsSampleCount = 0L;
    private int fpsMin = Integer.MAX_VALUE;
    private boolean ended = false;

    public WrappedCardScreen(@Nullable final Screen parent, final List<Card> cards) {
        super(Text.literal("Minecraft Wrapped"));
        this.parent = parent;
        this.cards = cards;
    }

    private float renderScale() {
        if (width <= 0 || height <= 0) return 1f;
        return Math.min(width / DESIGN_W, height / DESIGN_H);
    }

    private int virtualWidth() {
        return Math.round(width / renderScale());
    }

    private int virtualHeight() {
        return Math.round(height / renderScale());
    }

    @Override
    protected void init() {
        if (!currentStarted && !cards.isEmpty()) {
            cards.get(currentIndex).start(MinecraftClient.getInstance(), virtualWidth(), virtualHeight());
            markCardStart();
            currentStarted = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (cards.isEmpty()) {
            close();
            return;
        }
        if (paused) return;

        final float multiplier = ConfigManager.get().speedMultiplier;
        if (multiplier <= 0.05f) {
            // Instant mode: jump straight to the final card on the first frame.
            jumpToCard(cards.size() - 1);
            return;
        }
        speedAccumulator += multiplier;
        int innerTicks = (int) speedAccumulator;
        speedAccumulator -= innerTicks;
        // Cap inner ticks to avoid runaway loops after a stutter.
        if (innerTicks > 4) innerTicks = 4;
        for (int i = 0; i < innerTicks; i++) tickOnce();
    }

    private void tickOnce() {
        final int fps = MinecraftClient.getInstance().getCurrentFps();
        if (fps > 0) {
            fpsSampleSum += fps;
            fpsSampleCount++;
            if (fps < fpsMin) fpsMin = fps;
        }
        final int vw = virtualWidth();
        final int vh = virtualHeight();

        if (transitionTicks >= 0) {
            transitionTicks++;
            if (transitionTicks == TRANSITION_HALF) advanceCard(vw, vh);
            if (transitionTicks >= TRANSITION_HALF * 2) transitionTicks = -1;
            return;
        }

        final Card current = cards.get(currentIndex);
        current.tick(vw, vh);
        if (current.isDone()) {
            emitCardViewed(currentIndex);
            if (currentIndex >= cards.size() - 1) {
                endRun(false);
                close();
            } else {
                transitionTicks = 0;
            }
        }
    }

    private void advanceCard(final int vw, final int vh) {
        currentIndex++;
        if (currentIndex < cards.size()) {
            cards.get(currentIndex).start(MinecraftClient.getInstance(), vw, vh);
            markCardStart();
        }
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        if (cards.isEmpty()) return;
        final float scale = renderScale();
        final int vw = virtualWidth();
        final int vh = virtualHeight();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        final int virtMouseX = Math.round(mouseX / scale);
        final int virtMouseY = Math.round(mouseY / scale);
        cards.get(currentIndex).render(context, vw, vh, virtMouseX, virtMouseY, delta);
        context.getMatrices().popMatrix();

        if (transitionTicks >= 0) {
            renderTransition(context, delta);
        }
    }

    private void renderTransition(final DrawContext context, final float delta) {
        final float t = (transitionTicks + delta) / (TRANSITION_HALF * 2);
        final float clampedT = Math.max(0f, Math.min(1f, t));
        final float coverage = clampedT < 0.5f ? clampedT * 2f : (1f - clampedT) * 2f;
        final TransitionStyle style = ConfigManager.get().transition;
        switch (style) {
            case CUT -> { /* no overlay — the advance-at-midpoint flips cards instantly */ }
            case SLIDE -> {
                // Black bar that wipes left→right during the first half, right→left during the second.
                final int barX = (int) (clampedT < 0.5f
                        ? clampedT * 2f * width
                        : (1f - (clampedT - 0.5f) * 2f) * width);
                final int barWidth = Math.max(width / 6, 40);
                context.fill(barX - barWidth, 0, barX, height, 0xFF000000);
                context.fill(barX, 0, barX + barWidth, height, 0xFF000000);
            }
            case ZOOM -> {
                // Black rectangle expanding from the center then shrinking.
                final int halfW = (int) (coverage * width / 2);
                final int halfH = (int) (coverage * height / 2);
                final int cx = width / 2, cy = height / 2;
                context.fill(cx - halfW, cy - halfH, cx + halfW, cy + halfH, 0xFF000000);
            }
            case FADE -> {
                final int alpha = (int) (coverage * 255) & 0xFF;
                context.fill(0, 0, width, height, alpha << 24);
            }
        }
    }

    @Override
    public void renderBackground(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        // Cards draw their own background.
    }

    @Override
    public boolean keyPressed(final KeyInput input) {
        final int key = input.key();
        if (cards.isEmpty()) return super.keyPressed(input);
        if (key == GLFW.GLFW_KEY_SPACE) {
            paused = !paused;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            emitCardViewed(currentIndex);
            endRun(true);
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            jumpToCard(currentIndex + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            jumpToCard(currentIndex - 1);
            return true;
        }
        return super.keyPressed(input);
    }

    private void jumpToCard(final int targetIndex) {
        if (targetIndex < 0 || targetIndex >= cards.size()) return;
        emitCardViewed(currentIndex);
        currentIndex = targetIndex;
        transitionTicks = -1;
        cards.get(currentIndex).start(MinecraftClient.getInstance(), virtualWidth(), virtualHeight());
        markCardStart();
    }

    private void markCardStart() {
        cardStartMillis = System.currentTimeMillis();
    }

    private void emitCardViewed(final int index) {
        if (index < 0 || index >= cards.size()) return;
        final long durationMs = System.currentTimeMillis() - cardStartMillis;
        Telemetry.capture(Events.CARD_VIEWED, Map.of(
                "card_id", cards.get(index).analyticsId(),
                "index", index,
                "duration_ms", durationMs));
    }

    /** Fires once when the run ends. {@code skipped} distinguishes ESC from natural completion. */
    private void endRun(final boolean skipped) {
        if (ended) return;
        ended = true;

        if (skipped) {
            Telemetry.capture(Events.WRAPPED_SKIPPED, Map.of(
                    "card_id", cards.isEmpty() ? "none" : cards.get(currentIndex).analyticsId(),
                    "index", currentIndex));
        } else {
            Telemetry.capture(Events.WRAPPED_COMPLETED, Map.of(
                    "cards_viewed", cards.size(),
                    "completion_pct", 100));
        }

        if (fpsSampleCount > 0) {
            final Map<String, Object> fpsProps = new HashMap<>();
            fpsProps.put("avg_fps", (int) (fpsSampleSum / fpsSampleCount));
            fpsProps.put("min_fps", fpsMin == Integer.MAX_VALUE ? 0 : fpsMin);
            Telemetry.capture(Events.ANIMATION_FPS, fpsProps);
        }
    }

    @Override
    public boolean mouseClicked(final Click click, final boolean doubled) {
        if (cards.isEmpty() || transitionTicks >= 0) return super.mouseClicked(click, doubled);
        final float scale = renderScale();
        final double vx = click.x() / scale;
        final double vy = click.y() / scale;
        if (cards.get(currentIndex).mouseClicked(vx, vy, click.button())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.setScreen(parent);
    }
}
