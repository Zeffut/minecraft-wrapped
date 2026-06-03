package fr.zeffut.mcwrapped.config.ui;

import fr.zeffut.mcwrapped.config.AspectRatio;
import fr.zeffut.mcwrapped.config.ColorTheme;
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.config.GradientDirection;
import fr.zeffut.mcwrapped.config.McWrappedConfig;
import fr.zeffut.mcwrapped.config.SparkleDensity;
import fr.zeffut.mcwrapped.config.TransitionStyle;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import fr.zeffut.mcwrapped.ui.cards.CardEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single-window tabbed configuration screen. Six tabs, ~25 widgets total. Mutations are saved
 * eagerly through {@link ConfigManager#save()} so the user can preview changes by replaying
 * {@code /wrapped} at any time.
 */
public final class McWrappedConfigScreen extends Screen {

    private static final List<String> TABS = List.of("Visual", "Cards", "Animation", "Export", "Privacy", "Trigger");
    private static final int TAB_H = 22;
    /** Hard cap on the content column. Below this the layout shrinks proportionally. */
    private static final int MAX_CONTENT_W = 520;

    @Nullable private final Screen parent;
    private int activeTab = 0;
    private boolean openTracked = false;

    // ---- Responsive layout helpers — recomputed every init() because width/height change.

    private int contentW() { return Math.min(width - 40, MAX_CONTENT_W); }
    private int xStart()   { return width / 2 - contentW() / 2; }
    /** Label column width. ~38% of content; minimum 80px so short labels stay readable. */
    private int labelW()   { return Math.max(80, (int) (contentW() * 0.38)); }
    private int xLabel()   { return xStart(); }
    private int xField()   { return xStart() + labelW() + 8; }
    private int fieldW()   { return contentW() - labelW() - 8; }
    private int rowH()     { return 22; }
    /** Available height between the tab bar (bottom ~58) and the action bar (top ~height-38). */
    private int contentTop() { return 64; }

    public McWrappedConfigScreen(@Nullable final Screen parent) {
        super(Text.literal("Minecraft Wrapped — Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!openTracked) {
            Telemetry.capture(Events.CONFIG_OPENED, Map.of());
            openTracked = true;
        }
        // Top tab bar — width adapts to the window so the 6 tabs always fit.
        final int tabGap = 4;
        final int tabsTotalW = Math.min(width - 20, MAX_CONTENT_W + 60);
        final int tabW = (tabsTotalW - tabGap * (TABS.size() - 1)) / TABS.size();
        final int tabStartX = width / 2 - tabsTotalW / 2;
        for (int i = 0; i < TABS.size(); i++) {
            final int idx = i;
            final boolean active = idx == activeTab;
            final Text label = Text.literal(TABS.get(i)).formatted(active ? Formatting.GOLD : Formatting.GRAY);
            addDrawableChild(ButtonWidget.builder(label, btn -> {
                activeTab = idx;
                rebuild();
            }).dimensions(tabStartX + i * (tabW + tabGap), 30, tabW, TAB_H).build());
        }

        // Tab content.
        switch (activeTab) {
            case 0 -> buildVisual();
            case 1 -> buildCards();
            case 2 -> buildAnimation();
            case 3 -> buildExport();
            case 4 -> buildPrivacy();
            case 5 -> buildTrigger();
        }

        // Bottom Reset / Done bar — also responsive.
        final int actionW = Math.min(160, (contentW() - 20) / 2);
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset all defaults"), btn -> {
            ConfigManager.get().resetToDefaults();
            ConfigManager.save();
            rebuild();
        }).dimensions(width / 2 - actionW - 5, height - 28, actionW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done").formatted(Formatting.GREEN), btn -> {
            ConfigManager.save();
            close();
        }).dimensions(width / 2 + 5, height - 28, actionW, 20).build());
    }

    // --------------------------------------------------------------- Tabs --

    private void buildVisual() {
        final McWrappedConfig cfg = ConfigManager.get();
        int y = contentTop();
        final int xLabel = xLabel();
        final int xField = xField();
        final int fieldW = fieldW();
        final int rowH = rowH();

        // Theme cycle.
        addRow(y, xLabel, xField, fieldW, "Theme",
                ButtonWidget.builder(Text.literal(cfg.theme.displayName()), btn -> cycleTheme(cfg))
                        .dimensions(xField, y, fieldW, rowH).build());
        y += rowH + 6;

        // Custom colors visible only when CUSTOM.
        final boolean custom = cfg.theme == ColorTheme.CUSTOM;
        if (custom) {
            addColorRow(y, xLabel, xField, fieldW, "BG Top",   cfg.customBgTop,
                    v -> { cfg.customBgTop = v; ConfigManager.save(); });
            y += rowH + 6;
            addColorRow(y, xLabel, xField, fieldW, "BG Bottom", cfg.customBgBottom,
                    v -> { cfg.customBgBottom = v; ConfigManager.save(); });
            y += rowH + 6;
            addColorRow(y, xLabel, xField, fieldW, "Accent",    cfg.customAccent,
                    v -> { cfg.customAccent = v; ConfigManager.save(); });
            y += rowH + 6;
            addColorRow(y, xLabel, xField, fieldW, "Accent 2",  cfg.customAccentSecondary,
                    v -> { cfg.customAccentSecondary = v; ConfigManager.save(); });
            y += rowH + 6;
        }

        // Gradient direction.
        addRow(y, xLabel, xField, fieldW, "Gradient",
                ButtonWidget.builder(Text.literal(cfg.gradient.displayName()), btn -> {
                    cfg.gradient = cycle(cfg.gradient, GradientDirection.values());
                    ConfigManager.save();
                    recordChange("gradient", "(changed)", cfg.gradient.name());
                    rebuild();
                }).dimensions(xField, y, fieldW, rowH).build());
        y += rowH + 6;

        // Custom title.
        final TextFieldWidget titleField = new TextFieldWidget(textRenderer, xField, y, fieldW, rowH, Text.literal("title"));
        titleField.setMaxLength(64);
        titleField.setText(cfg.customTitle);
        titleField.setChangedListener(s -> { cfg.customTitle = s; ConfigManager.save(); });
        addRow(y, xLabel, xField, fieldW, "Custom title", titleField);
    }

    private void cycleTheme(final McWrappedConfig cfg) {
        cfg.theme = cycle(cfg.theme, ColorTheme.values());
        ConfigManager.save();
        recordChange("theme", "(changed)", cfg.theme.name());
        rebuild();
    }

    private void buildCards() {
        // Just one CTA + a summary, sized to fit the responsive content column.
        final int btnW = contentW();
        final int xBtn = xStart();
        final int countEnabled = (int) ConfigManager.get().enabledCards.values().stream().filter(b -> b).count();
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Card order & visibility — " + countEnabled + "/" + ConfigManager.get().enabledCards.size() + " enabled"),
                btn -> client.setScreen(new CardOrderScreen(this)))
                .dimensions(xBtn, height / 2 - 12, btnW, 24).build());
    }

    private void buildAnimation() {
        final McWrappedConfig cfg = ConfigManager.get();
        int y = contentTop();
        final int xLabel = xLabel();
        final int xField = xField();
        final int fieldW = fieldW();
        final int rowH = rowH();

        // Speed multiplier.
        addRow(y, xLabel, xField, fieldW, "Speed",
                new ConfigSlider(xField, y, fieldW, rowH, 0.0, 2.0, cfg.speedMultiplier,
                        v -> v < 0.05 ? "Instant" : String.format(Locale.ROOT, "%.2fx", v),
                        v -> { cfg.speedMultiplier = (float) v; ConfigManager.save(); }));
        y += rowH + 6;

        // Sound volume.
        addRow(y, xLabel, xField, fieldW, "Sound volume",
                new ConfigSlider(xField, y, fieldW, rowH, 0, 100, cfg.soundVolume,
                        v -> Math.round(v) + "%",
                        v -> { cfg.soundVolume = (int) Math.round(v); ConfigManager.save(); }));
        y += rowH + 6;

        // Sparkle density (cycle).
        addRow(y, xLabel, xField, fieldW, "Sparkles",
                ButtonWidget.builder(Text.literal(cfg.sparkleDensity.displayName()), btn -> {
                    cfg.sparkleDensity = cycle(cfg.sparkleDensity, SparkleDensity.values());
                    ConfigManager.save();
                    recordChange("sparkleDensity", "(changed)", cfg.sparkleDensity.name());
                    rebuild();
                }).dimensions(xField, y, fieldW, rowH).build());
        y += rowH + 6;

        // Transition (cycle).
        addRow(y, xLabel, xField, fieldW, "Transition",
                ButtonWidget.builder(Text.literal(cfg.transition.displayName()), btn -> {
                    cfg.transition = cycle(cfg.transition, TransitionStyle.values());
                    ConfigManager.save();
                    recordChange("transition", "(changed)", cfg.transition.name());
                    rebuild();
                }).dimensions(xField, y, fieldW, rowH).build());
    }

    private void buildExport() {
        final McWrappedConfig cfg = ConfigManager.get();
        int y = contentTop();
        final int xLabel = xLabel();
        final int xField = xField();
        final int fieldW = fieldW();
        final int rowH = rowH();

        addRow(y, xLabel, xField, fieldW, "Aspect ratio",
                ButtonWidget.builder(Text.literal(cfg.aspectRatio.displayName()), btn -> {
                    cfg.aspectRatio = cycle(cfg.aspectRatio, AspectRatio.values());
                    ConfigManager.save();
                    recordChange("aspectRatio", "(changed)", cfg.aspectRatio.name());
                    rebuild();
                }).dimensions(xField, y, fieldW, rowH).build());
        y += rowH + 6;

        addRow(y, xLabel, xField, fieldW, "Skin head watermark",
                booleanButton(cfg.watermarkSkinHead, xField, y, fieldW, rowH,
                        v -> { cfg.watermarkSkinHead = v; ConfigManager.save(); rebuild(); }));
        y += rowH + 6;

        final TextFieldWidget sig = new TextFieldWidget(textRenderer, xField, y, fieldW, rowH, Text.literal("sig"));
        sig.setMaxLength(64);
        sig.setText(cfg.signature);
        sig.setChangedListener(s -> { cfg.signature = s; ConfigManager.save(); });
        addRow(y, xLabel, xField, fieldW, "Signature", sig);
    }

    private void buildPrivacy() {
        final McWrappedConfig cfg = ConfigManager.get();
        int y = contentTop();
        final int xLabel = xLabel();
        final int xField = xField();
        final int fieldW = fieldW();
        final int rowH = rowH();

        addRow(y, xLabel, xField, fieldW, "Mask server names",
                booleanButton(cfg.maskServerNames, xField, y, fieldW, rowH,
                        v -> { cfg.maskServerNames = v; ConfigManager.save(); rebuild(); }));
        y += rowH + 6;

        addRow(y, xLabel, xField, fieldW, "Mask IGN",
                booleanButton(cfg.maskIgn, xField, y, fieldW, rowH,
                        v -> { recordChange("maskIgn", !v, v); cfg.maskIgn = v; ConfigManager.save(); rebuild(); }));
        y += rowH + 6;

        addRow(y, xLabel, xField, fieldW, "Telemetry",
                booleanButton(cfg.telemetryEnabled, xField, y, fieldW, rowH, v -> {
                    final boolean was = cfg.telemetryEnabled;
                    if (was && !v) {
                        Telemetry.captureIgnoringConsent(Events.TELEMETRY_OPT_OUT, Map.of());
                    }
                    cfg.telemetryEnabled = v;
                    ConfigManager.save();
                    if (!was && v) {
                        Telemetry.capture(Events.TELEMETRY_OPT_IN, Map.of());
                    }
                    recordChange("telemetryEnabled", was, v);
                    rebuild();
                }));
        y += rowH + 6;

        // Hint about world filter editing.
        // (No widget — rendered below in render().)
    }

    private void buildTrigger() {
        final McWrappedConfig cfg = ConfigManager.get();
        int y = contentTop();
        final int xLabel = xLabel();
        final int xField = xField();
        final int fieldW = fieldW();
        final int rowH = rowH();

        addRow(y, xLabel, xField, fieldW, "Auto-trigger",
                booleanButton(cfg.autoTriggerEnabled, xField, y, fieldW, rowH,
                        v -> { recordChange("autoTriggerEnabled", !v, v); cfg.autoTriggerEnabled = v; ConfigManager.save(); rebuild(); }));
        y += rowH + 6;

        addRow(y, xLabel, xField, fieldW, "Grace window",
                new ConfigSlider(xField, y, fieldW, rowH, 1, 31, cfg.autoTriggerGraceDays,
                        v -> "1–" + Math.round(v) + " of the month",
                        v -> { cfg.autoTriggerGraceDays = (int) Math.round(v); ConfigManager.save(); }));
        y += rowH + 6;

        // Target month text field (empty = previous month).
        final TextFieldWidget tm = new TextFieldWidget(textRenderer, xField, y, fieldW, rowH, Text.literal("YYYY-MM"));
        tm.setMaxLength(7);
        tm.setText(cfg.targetMonth);
        tm.setChangedListener(s -> { cfg.targetMonth = s.trim(); ConfigManager.save(); });
        addRow(y, xLabel, xField, fieldW, "Target month", tm);
    }

    // ------------------------------------------------------------ Helpers --

    private void addRow(final int y, final int xLabel, final int xField, final int fieldW,
                        final String label, final net.minecraft.client.gui.widget.ClickableWidget widget) {
        // Label rendered in render(); widget added as drawable child.
        labels.add(new LabelEntry(label, xLabel, y));
        addDrawableChild(widget);
    }

    private void addColorRow(final int y, final int xLabel, final int xField, final int fieldW,
                             final String label, final int currentArgb, final java.util.function.IntConsumer onChange) {
        labels.add(new LabelEntry(label, xLabel, y));
        addDrawableChild(new HexColorField(xField, y, fieldW, 22, currentArgb, onChange));
    }

    private ButtonWidget booleanButton(final boolean value, final int x, final int y, final int w, final int h,
                                       final java.util.function.Consumer<Boolean> onChange) {
        final Text text = Text.literal(value ? "ON" : "OFF").formatted(value ? Formatting.GREEN : Formatting.GRAY);
        return ButtonWidget.builder(text, btn -> onChange.accept(!value)).dimensions(x, y, w, h).build();
    }

    private static <T extends Enum<T>> T cycle(final T current, final T[] values) {
        final int next = (current.ordinal() + 1) % values.length;
        return values[next];
    }

    private static void recordChange(final String setting, final Object oldValue, final Object newValue) {
        Telemetry.capture(Events.CONFIG_CHANGED, Map.of(
                "setting", setting,
                "old_value", String.valueOf(oldValue),
                "new_value", String.valueOf(newValue)));
    }

    // Labels are not widgets — we render them ourselves so they look like field captions.
    private final java.util.List<LabelEntry> labels = new java.util.ArrayList<>();
    private record LabelEntry(String text, int x, int y) {}

    private void rebuild() {
        labels.clear();
        clearChildren();
        init();
    }

    @Override
    public void render(final DrawContext ctx, final int mouseX, final int mouseY, final float delta) {
        CardEffects.renderGradient(ctx, width, height, CardEffects.BG_TOP, CardEffects.BG_BOTTOM);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("MINECRAFT WRAPPED — SETTINGS").formatted(Formatting.GOLD),
                width / 2, 12, 0xFFFFFFFF);

        for (final LabelEntry l : labels) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(l.text), l.x, l.y + 7, 0xFFE5E7EB);
        }

        if (activeTab == 4) {
            // Privacy hint.
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("World filter: edit config/mcwrapped.json → includedWorlds / excludedWorlds")
                            .formatted(Formatting.GRAY),
                    width / 2, height - 70, 0xFFAAB0BB);
        }
        if (activeTab == 5) {
            // Trigger hint.
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Target month: leave empty to use the previous month automatically")
                            .formatted(Formatting.GRAY),
                    width / 2, height - 70, 0xFFAAB0BB);
        }
    }

    @Override
    public void renderBackground(final DrawContext ctx, final int mouseX, final int mouseY, final float delta) {
        // Painted in render().
    }

    @Override
    public void close() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.setScreen(parent);
    }

    @SuppressWarnings("unused")
    private static <T> List<T> immutable(final T[] arr) { return Arrays.asList(arr); }
}
