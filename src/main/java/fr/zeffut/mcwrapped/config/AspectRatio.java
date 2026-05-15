package fr.zeffut.mcwrapped.config;

/**
 * Export image aspect ratios. Width/height are the target PNG dimensions.
 */
public enum AspectRatio {
    STORY_9_16("Story (9:16)", 1080, 1920),
    POST_1_1("Square (1:1)", 1080, 1080),
    LANDSCAPE_16_9("Landscape (16:9)", 1920, 1080);

    private final String displayName;
    private final int width;
    private final int height;

    AspectRatio(final String displayName, final int width, final int height) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
    }

    public String displayName() { return displayName; }
    public int width() { return width; }
    public int height() { return height; }
}
