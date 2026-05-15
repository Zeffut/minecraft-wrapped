package fr.zeffut.mcwrapped.config;

/**
 * Stable identifiers for every card the sequence can render. Used by config
 * to enable/disable and reorder cards.
 */
public enum CardId {
    INTRO("Intro"),
    TIME_SPENT("Time Spent"),
    LONGEST_SESSION("Longest Session"),
    TIME_OF_DAY("When You Play"),
    TOP_WORLD("Top World"),
    DIMENSION("Dimensions Explored"),
    SOCIAL("Social"),
    DISTANCE("Distance Covered"),
    TOP_BLOCKS("Top Blocks Mined"),
    TOP_MOB("Top Mob Killed"),
    CRAFTING("Top Crafted"),
    DEATH_RECAP("Death Recap"),
    ARCHETYPE("Archetype"),
    FINAL("Final");

    private final String displayName;

    CardId(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
