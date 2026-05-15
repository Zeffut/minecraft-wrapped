package fr.zeffut.mcwrapped.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable settings model serialized to {@code config/mcwrapped.json}.
 *
 * <p>Every field has a sensible default; fields are read live by the rest of the mod through
 * {@link ConfigManager#get()}. Unknown JSON keys are ignored on load and missing fields fall back
 * to defaults, so the file stays forward- and backward-compatible.
 */
public final class McWrappedConfig {

    // ---- A. Identité visuelle ---------------------------------------------
    public ColorTheme theme = ColorTheme.DEFAULT;
    public int customBgTop = 0xFF0F0F23;
    public int customBgBottom = 0xFF1E1B4B;
    public int customAccent = 0xFF22C55E;
    public int customAccentSecondary = 0xFF4338CA;
    public GradientDirection gradient = GradientDirection.VERTICAL;

    // ---- B. Contenu --------------------------------------------------------
    /** Per-card enable/disable. Any missing CardId is treated as enabled. */
    public Map<CardId, Boolean> enabledCards = defaultEnabled();
    /** Display order of cards. Missing CardIds are appended at the end in declaration order. */
    public List<CardId> cardOrder = defaultOrder();
    /** Empty string = use the previous month automatically. Format YYYY-MM otherwise. */
    public String targetMonth = "";
    /** If non-empty, only worlds whose key is in the set count. */
    public Set<String> includedWorlds = new HashSet<>();
    /** Worlds explicitly hidden from the wrapped. Always applied after include filter. */
    public Set<String> excludedWorlds = new HashSet<>();
    /** Custom hero title. Empty = default localized "YOUR <MONTH> WRAPPED". */
    public String customTitle = "";

    // ---- C. Animation & sound ---------------------------------------------
    /** 0.5 to 2.0 (and 0.0 = instant skip). 1.0 = original timing. */
    public float speedMultiplier = 1.0f;
    /** 0..100 user volume applied multiplicatively to every wrapped sound. */
    public int soundVolume = 100;
    public SparkleDensity sparkleDensity = SparkleDensity.MEDIUM;
    public TransitionStyle transition = TransitionStyle.FADE;

    // ---- D. Format & export -----------------------------------------------
    public AspectRatio aspectRatio = AspectRatio.STORY_9_16;
    public boolean watermarkSkinHead = true;
    public String signature = "";

    // ---- E. Privacy --------------------------------------------------------
    public boolean maskServerNames = false;
    public boolean maskIgn = false;

    // ---- F. Trigger --------------------------------------------------------
    public boolean autoTriggerEnabled = true;
    /** 1..31 days after month rollover where the title-screen prompt appears. */
    public int autoTriggerGraceDays = 7;

    // ---- Defaults helpers --------------------------------------------------

    private static Map<CardId, Boolean> defaultEnabled() {
        final EnumMap<CardId, Boolean> m = new EnumMap<>(CardId.class);
        for (final CardId id : CardId.values()) m.put(id, true);
        return m;
    }

    private static List<CardId> defaultOrder() {
        return new ArrayList<>(List.of(CardId.values()));
    }

    /** Replaces all fields with library defaults. Used by the "Reset" button in the config screen. */
    public void resetToDefaults() {
        final McWrappedConfig d = new McWrappedConfig();
        this.theme = d.theme;
        this.customBgTop = d.customBgTop;
        this.customBgBottom = d.customBgBottom;
        this.customAccent = d.customAccent;
        this.customAccentSecondary = d.customAccentSecondary;
        this.gradient = d.gradient;
        this.enabledCards = d.enabledCards;
        this.cardOrder = d.cardOrder;
        this.targetMonth = d.targetMonth;
        this.includedWorlds = d.includedWorlds;
        this.excludedWorlds = d.excludedWorlds;
        this.customTitle = d.customTitle;
        this.speedMultiplier = d.speedMultiplier;
        this.soundVolume = d.soundVolume;
        this.sparkleDensity = d.sparkleDensity;
        this.transition = d.transition;
        this.aspectRatio = d.aspectRatio;
        this.watermarkSkinHead = d.watermarkSkinHead;
        this.signature = d.signature;
        this.maskServerNames = d.maskServerNames;
        this.maskIgn = d.maskIgn;
        this.autoTriggerEnabled = d.autoTriggerEnabled;
        this.autoTriggerGraceDays = d.autoTriggerGraceDays;
    }

    // ---- Resolved getters --------------------------------------------------

    /** Background top color resolved through the theme (or custom override). */
    public int resolvedBgTop() {
        return theme == ColorTheme.CUSTOM ? customBgTop : theme.bgTop();
    }

    public int resolvedBgBottom() {
        return theme == ColorTheme.CUSTOM ? customBgBottom : theme.bgBottom();
    }

    public int resolvedAccent() {
        return theme == ColorTheme.CUSTOM ? customAccent : theme.accent();
    }

    public boolean isCardEnabled(final CardId id) {
        return enabledCards.getOrDefault(id, Boolean.TRUE);
    }
}
