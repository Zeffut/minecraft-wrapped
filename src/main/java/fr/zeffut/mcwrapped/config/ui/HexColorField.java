package fr.zeffut.mcwrapped.config.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/**
 * Text field accepting hex color strings like {@code #RRGGBB} or {@code RRGGBB}. Forwards a fully
 * opaque ARGB int to {@link #onValid} every time the user types a valid 6-hex input.
 */
public final class HexColorField extends TextFieldWidget {

    private static final int VALID_BORDER = 0xFF22C55E;
    private static final int INVALID_BORDER = 0xFFEF4444;
    private final IntConsumer onValid;
    private boolean lastValid = true;

    public HexColorField(final int x, final int y, final int w, final int h, final int initialArgb, final IntConsumer onValid) {
        super(MinecraftClient.getInstance().textRenderer, x, y, w, h, Text.literal("hex"));
        this.onValid = onValid;
        setMaxLength(7);
        setText(String.format("#%06X", initialArgb & 0xFFFFFF));
        setChangedListener(this::onText);
    }

    private void onText(final String text) {
        final String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() == 6 && hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            final int rgb = Integer.parseInt(hex, 16);
            onValid.accept(0xFF000000 | rgb);
            lastValid = true;
        } else {
            lastValid = false;
        }
    }

    @Override
    public void renderWidget(final DrawContext ctx, final int mouseX, final int mouseY, final float delta) {
        super.renderWidget(ctx, mouseX, mouseY, delta);
        // Visual cue: green ring when value parses, red when not.
        final int color = lastValid ? VALID_BORDER : INVALID_BORDER;
        ctx.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY(), color);
        ctx.fill(getX() - 1, getY() + getHeight(), getX() + getWidth() + 1, getY() + getHeight() + 1, color);
        ctx.fill(getX() - 1, getY(), getX(), getY() + getHeight(), color);
        ctx.fill(getX() + getWidth(), getY(), getX() + getWidth() + 1, getY() + getHeight(), color);
    }
}
