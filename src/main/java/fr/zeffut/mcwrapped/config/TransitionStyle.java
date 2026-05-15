package fr.zeffut.mcwrapped.config;

public enum TransitionStyle {
    FADE("Fade"),
    SLIDE("Slide"),
    ZOOM("Zoom"),
    CUT("Cut");

    private final String displayName;

    TransitionStyle(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }
}
