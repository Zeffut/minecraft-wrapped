package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.ui.cards.Card;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import org.jetbrains.annotations.Nullable;

/**
 * Hosts a single {@link Card}. The screen drives the card's tick + render lifecycle
 * and returns to {@code parent} when the card finishes (or the user presses ESC).
 */
public final class WrappedCardScreen extends Screen {

    @Nullable
    private final Screen parent;
    private final Card card;
    private boolean started = false;

    public WrappedCardScreen(@Nullable final Screen parent, final Card card) {
        super(Text.literal("Minecraft Wrapped"));
        this.parent = parent;
        this.card = card;
    }

    @Override
    protected void init() {
        if (!started) {
            card.start(MinecraftClient.getInstance(), width, height);
            started = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        card.tick(width, height);
        if (card.isDone()) {
            close();
        }
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        card.render(context, width, height, delta);
    }

    @Override
    public void renderBackground(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        // The card draws its own background.
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.setScreen(parent);
        }
    }
}
