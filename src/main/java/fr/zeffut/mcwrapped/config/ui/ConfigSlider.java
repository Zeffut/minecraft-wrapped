package fr.zeffut.mcwrapped.config.ui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Generic numeric slider. Converts a logical {@code min..max} value into the AbstractSliderButton's 0..1
 * range and back, formats the label via {@link #labelFn}, and pushes changes through
 * {@link #onChange}.
 */
public final class ConfigSlider extends AbstractSliderButton {

    private final double min;
    private final double max;
    private final DoubleFunction<String> labelFn;
    private final DoubleConsumer onChange;

    public ConfigSlider(final int x, final int y, final int w, final int h,
                        final double min, final double max, final double initial,
                        final DoubleFunction<String> labelFn,
                        final DoubleConsumer onChange) {
        super(x, y, w, h, Component.empty(), clamp01((initial - min) / (max - min)));
        this.min = min;
        this.max = max;
        this.labelFn = labelFn;
        this.onChange = onChange;
        updateMessage();
    }

    /** Convenience for "%.1f"-formatted floats. */
    public static String fmt(final double v, final int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    public double logicalValue() {
        return min + value * (max - min);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(labelFn.apply(logicalValue())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(logicalValue());
    }

    private static double clamp01(final double v) {
        return Math.max(0, Math.min(1, v));
    }
}
