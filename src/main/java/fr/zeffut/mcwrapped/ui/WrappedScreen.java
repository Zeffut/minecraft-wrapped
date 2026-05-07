package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.stats.MonthlyDelta;
import fr.zeffut.mcwrapped.stats.WrappedFile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/**
 * Placeholder S1 screen — plain text recap. The animated card experience lands in S2+.
 */
public final class WrappedScreen extends Screen {

    private final Screen parent;
    private final WrappedFile wrapped;

    public WrappedScreen(final Screen parent, final WrappedFile wrapped) {
        super(Text.literal("Wrapped — " + monthLabel(wrapped.month())));
        this.parent = parent;
        this.wrapped = wrapped;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> close())
                .dimensions(width / 2 - 75, height - 28, 150, 20)
                .build());
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        super.render(context, mouseX, mouseY, delta);

        final MonthlyDelta d = wrapped.delta();
        final Map<String, Long> custom = d.deltas().getOrDefault("minecraft:custom", Map.of());

        int y = 30;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Your " + monthLabel(wrapped.month()) + " Wrapped").formatted(net.minecraft.util.Formatting.GOLD),
                width / 2, y, 0xFFFFFFFF);
        y += 30;

        final long playTicks = custom.getOrDefault("minecraft:play_time", 0L);
        line(context, y, "Play time", playTicks / 20 / 60 + " min"); y += 14;
        line(context, y, "Deaths", String.valueOf(custom.getOrDefault("minecraft:deaths", 0L))); y += 14;
        line(context, y, "Mob kills", String.valueOf(custom.getOrDefault("minecraft:mob_kills", 0L))); y += 14;
        line(context, y, "Jumps", String.valueOf(custom.getOrDefault("minecraft:jump", 0L))); y += 14;
        line(context, y, "Distance walked (cm)", String.valueOf(custom.getOrDefault("minecraft:walk_one_cm", 0L))); y += 14;
        line(context, y, "Distance sprinted (cm)", String.valueOf(custom.getOrDefault("minecraft:sprint_one_cm", 0L))); y += 14;
        y += 6;
        line(context, y, "Blocks mined (total)", String.valueOf(d.total("minecraft:mined"))); y += 14;
        line(context, y, "Mobs killed (total)", String.valueOf(d.total("minecraft:killed"))); y += 14;
        line(context, y, "Items used (total)", String.valueOf(d.total("minecraft:used"))); y += 14;
        line(context, y, "Items crafted (total)", String.valueOf(d.total("minecraft:crafted"))); y += 14;
        y += 10;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("S1 placeholder — animated cards coming in S2+").formatted(net.minecraft.util.Formatting.GRAY),
                width / 2, y, 0xFFAAAAAA);
    }

    private void line(final DrawContext ctx, final int y, final String label, final String value) {
        final int x = width / 2 - 120;
        ctx.drawTextWithShadow(textRenderer, Text.literal(label).formatted(net.minecraft.util.Formatting.GRAY), x, y, 0xFFCCCCCC);
        ctx.drawTextWithShadow(textRenderer, Text.literal(value).formatted(net.minecraft.util.Formatting.WHITE), x + 160, y, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private static String monthLabel(final YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }
}
