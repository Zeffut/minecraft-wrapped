package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.ui.cards.Card;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

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

    public WrappedCardScreen(@Nullable final Screen parent, final List<Card> cards) {
        super(Text.literal("Minecraft Wrapped"));
        this.parent = parent;
        this.cards = cards;
    }

    private float renderScale() {
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
            if (currentIndex >= cards.size() - 1) close();
            else transitionTicks = 0;
        }
    }

    private void advanceCard(final int vw, final int vh) {
        currentIndex++;
        if (currentIndex < cards.size()) {
            cards.get(currentIndex).start(MinecraftClient.getInstance(), vw, vh);
        }
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        if (cards.isEmpty()) return;
        final float scale = renderScale();
        final int vw = virtualWidth();
        final int vh = virtualHeight();

        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1f);
        final int virtMouseX = Math.round(mouseX / scale);
        final int virtMouseY = Math.round(mouseY / scale);
        cards.get(currentIndex).render(context, vw, vh, virtMouseX, virtMouseY, delta);
        context.getMatrices().pop();

        if (transitionTicks >= 0) {
            final float t = (transitionTicks + delta) / (TRANSITION_HALF * 2);
            final float coverage = t < 0.5f ? t * 2f : (1f - t) * 2f;
            final int alpha = (int) (Math.max(0f, Math.min(1f, coverage)) * 255) & 0xFF;
            context.fill(0, 0, width, height, alpha << 24);
        }
    }

    @Override
    public void renderBackground(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        // Cards draw their own background.
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (cards.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            paused = !paused;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            jumpToCard(currentIndex + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            jumpToCard(currentIndex - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void jumpToCard(final int targetIndex) {
        if (targetIndex < 0 || targetIndex >= cards.size()) return;
        currentIndex = targetIndex;
        transitionTicks = -1;
        cards.get(currentIndex).start(MinecraftClient.getInstance(), virtualWidth(), virtualHeight());
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (cards.isEmpty() || transitionTicks >= 0) return super.mouseClicked(mouseX, mouseY, button);
        final float scale = renderScale();
        final double vx = mouseX / scale;
        final double vy = mouseY / scale;
        if (cards.get(currentIndex).mouseClicked(vx, vy, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
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
