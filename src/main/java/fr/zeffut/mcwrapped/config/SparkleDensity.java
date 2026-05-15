package fr.zeffut.mcwrapped.config;

public enum SparkleDensity {
    NONE("None", 0f),
    LOW("Low", 0.5f),
    MEDIUM("Medium", 1f),
    HIGH("High", 1.75f);

    private final String displayName;
    private final float multiplier;

    SparkleDensity(final String displayName, final float multiplier) {
        this.displayName = displayName;
        this.multiplier = multiplier;
    }

    public String displayName() { return displayName; }
    /** Multiplier applied to default sparkle counts. */
    public float multiplier() { return multiplier; }
}
