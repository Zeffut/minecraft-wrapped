package fr.zeffut.mcwrapped.ui.cards;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public interface Card {
    /** Called once when the card becomes visible. Use for sounds and one-shot setup. */
    void start(Minecraft client, int width, int height);

    /** Called on every game tick (20 Hz) while the card is showing. */
    void tick(int width, int height);

    /** Called every frame. {@code partialTick} is the fraction of the next tick already elapsed. */
    void render(GuiGraphics context, int width, int height, int mouseX, int mouseY, float partialTick);

    /** When true, the host screen closes the card. */
    boolean isDone();

    /** Optional click handling. Return true if consumed. */
    default boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }
}
