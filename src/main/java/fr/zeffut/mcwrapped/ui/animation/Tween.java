package fr.zeffut.mcwrapped.ui.animation;

/**
 * Animates a value between {@code from} and {@code to} over {@code durationTicks}.
 *
 * <p>Driven by integer ticks plus a sub-tick fraction for frame-smooth interpolation.
 */
public final class Tween {

    private final float from;
    private final float to;
    private final int durationTicks;
    private final int delayTicks;
    private final Easing easing;

    public Tween(final float from, final float to, final int durationTicks, final Easing easing) {
        this(from, to, durationTicks, 0, easing);
    }

    public Tween(final float from, final float to, final int durationTicks, final int delayTicks, final Easing easing) {
        this.from = from;
        this.to = to;
        this.durationTicks = Math.max(1, durationTicks);
        this.delayTicks = Math.max(0, delayTicks);
        this.easing = easing;
    }

    public float at(final int tick, final float partialTick) {
        final float elapsed = (tick - delayTicks) + partialTick;
        if (elapsed <= 0f) return from;
        if (elapsed >= durationTicks) return to;
        final float t = elapsed / durationTicks;
        return from + (to - from) * easing.apply(t);
    }

    public boolean isDone(final int tick) {
        return tick - delayTicks >= durationTicks;
    }
}
