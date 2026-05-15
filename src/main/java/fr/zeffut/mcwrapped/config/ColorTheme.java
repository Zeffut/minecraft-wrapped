package fr.zeffut.mcwrapped.config;

/**
 * Visual color presets. Each theme bundles a background gradient and an accent color.
 * {@link #CUSTOM} signals that the user-defined RGB values from
 * {@link McWrappedConfig#customBgTop} / {@code customBgBottom} / {@code customAccent} should be used.
 */
public enum ColorTheme {
    DEFAULT     ("Default",     0xFF0F0F23, 0xFF1E1B4B, 0xFF22C55E),
    NETHER      ("Nether",      0xFF1F0303, 0xFF4D0A0A, 0xFFFF5722),
    END         ("End",         0xFF0B0814, 0xFF1F1735, 0xFFD9B6FF),
    OCEAN       ("Ocean",       0xFF021E3D, 0xFF064E83, 0xFF38BDF8),
    SAKURA      ("Sakura",      0xFF2A0F1F, 0xFF53254A, 0xFFEC4899),
    HALLOWEEN   ("Halloween",   0xFF1B0F00, 0xFF402100, 0xFFF59E0B),
    CHRISTMAS   ("Christmas",   0xFF0A1F1A, 0xFF184C3A, 0xFFEF4444),
    VAPORWAVE   ("Vaporwave",   0xFF1A0033, 0xFF330066, 0xFFFF71CE),
    MONOCHROME  ("Monochrome",  0xFF0A0A0A, 0xFF1F1F1F, 0xFFE5E5E5),
    FOREST      ("Forest",      0xFF071A0E, 0xFF14401D, 0xFFA3E635),
    CUSTOM      ("Custom",      0xFF0F0F23, 0xFF1E1B4B, 0xFF22C55E);

    private final String displayName;
    private final int bgTop;
    private final int bgBottom;
    private final int accent;

    ColorTheme(final String displayName, final int bgTop, final int bgBottom, final int accent) {
        this.displayName = displayName;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.accent = accent;
    }

    public String displayName() { return displayName; }
    public int bgTop() { return bgTop; }
    public int bgBottom() { return bgBottom; }
    public int accent() { return accent; }
}
