package fr.zeffut.mcwrapped.config;

public enum GradientDirection {
    VERTICAL("Vertical"),
    DIAGONAL("Diagonal"),
    RADIAL("Radial");

    private final String displayName;

    GradientDirection(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }
}
