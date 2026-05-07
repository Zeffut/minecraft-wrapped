package fr.zeffut.mcwrapped.ui.animation;

@FunctionalInterface
public interface Easing {
    float apply(float t);

    Easing LINEAR = t -> t;

    Easing EASE_IN_QUAD = t -> t * t;
    Easing EASE_OUT_QUAD = t -> 1f - (1f - t) * (1f - t);
    Easing EASE_IN_OUT_QUAD = t -> t < 0.5f
            ? 2f * t * t
            : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;

    Easing EASE_IN_CUBIC = t -> t * t * t;
    Easing EASE_OUT_CUBIC = t -> 1f - (float) Math.pow(1f - t, 3);
    Easing EASE_IN_OUT_CUBIC = t -> t < 0.5f
            ? 4f * t * t * t
            : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;

    Easing EASE_OUT_BOUNCE = t -> {
        final float n1 = 7.5625f;
        final float d1 = 2.75f;
        float x = t;
        if (x < 1f / d1) {
            return n1 * x * x;
        } else if (x < 2f / d1) {
            x -= 1.5f / d1;
            return n1 * x * x + 0.75f;
        } else if (x < 2.5f / d1) {
            x -= 2.25f / d1;
            return n1 * x * x + 0.9375f;
        } else {
            x -= 2.625f / d1;
            return n1 * x * x + 0.984375f;
        }
    };
}
