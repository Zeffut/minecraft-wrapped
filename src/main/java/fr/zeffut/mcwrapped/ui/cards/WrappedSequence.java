package fr.zeffut.mcwrapped.ui.cards;

import fr.zeffut.mcwrapped.config.CardId;
import fr.zeffut.mcwrapped.config.ConfigManager;
import fr.zeffut.mcwrapped.config.McWrappedConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class WrappedSequence {

    private static final long MIN_PLAY_TICKS = 20L * 60;
    private static final long DEATHS_MIN_PLAY_TICKS = 20L * 60 * 30;
    private static final long SOCIAL_MIN_SERVER_TICKS = 20L * 60 * 5;

    private WrappedSequence() {}

    /**
     * Build the full ordered sequence using the user's configured order and enable flags.
     * Cards that don't pass their relevance threshold (e.g. zero distance) are still skipped — the
     * config only ever <em>reduces</em> the sequence; it never inserts cards that have no data.
     */
    public static List<Card> full(final WrappedContext context) {
        final McWrappedConfig cfg = ConfigManager.get();
        final Map<CardId, Supplier<Card>> factories = factories(context);
        final Map<CardId, Boolean> relevance = relevance(context);

        final List<Card> cards = new ArrayList<>();
        for (final CardId id : cfg.cardOrder) {
            if (!cfg.isCardEnabled(id)) continue;
            if (!relevance.getOrDefault(id, Boolean.FALSE)) continue;
            final Supplier<Card> factory = factories.get(id);
            if (factory != null) cards.add(factory.get());
        }
        // Ensure the user always sees at least the intro+final pair, even if their filters wiped
        // out everything in between.
        if (cards.isEmpty()) {
            cards.add(new IntroCard(context.month()));
            cards.add(new FinalCard(context));
        }
        return cards;
    }

    private static Map<CardId, Supplier<Card>> factories(final WrappedContext context) {
        final EnumMap<CardId, Supplier<Card>> m = new EnumMap<>(CardId.class);
        m.put(CardId.INTRO,            () -> new IntroCard(context.month()));
        m.put(CardId.TIME_SPENT,       () -> new TimeSpentCard(context));
        m.put(CardId.LONGEST_SESSION,  () -> new LongestSessionCard(context));
        m.put(CardId.TIME_OF_DAY,      () -> new TimeOfDayCard(context));
        m.put(CardId.TOP_WORLD,        () -> new TopWorldCard(context));
        m.put(CardId.DIMENSION,        () -> new DimensionCard(context));
        m.put(CardId.SOCIAL,           () -> new SocialCard(context));
        m.put(CardId.DISTANCE,         () -> new DistanceCard(context));
        m.put(CardId.TOP_BLOCKS,       () -> new TopBlocksCard(context));
        m.put(CardId.TOP_MOB,          () -> new TopMobCard(context));
        m.put(CardId.CRAFTING,         () -> new CraftingCard(context));
        m.put(CardId.DEATH_RECAP,      () -> new DeathRecapCard(context));
        m.put(CardId.ARCHETYPE,        () -> new ArchetypeCard(context));
        m.put(CardId.FINAL,            () -> new FinalCard(context));
        return m;
    }

    private static Map<CardId, Boolean> relevance(final WrappedContext context) {
        final EnumMap<CardId, Boolean> m = new EnumMap<>(CardId.class);
        m.put(CardId.INTRO, true);
        m.put(CardId.TIME_SPENT, context.playTimeTicks() >= MIN_PLAY_TICKS);
        m.put(CardId.LONGEST_SESSION, new LongestSessionCard(context).hasData());
        m.put(CardId.TIME_OF_DAY, new TimeOfDayCard(context).hasData());
        m.put(CardId.TOP_WORLD, !context.topWorlds(1).isEmpty());
        m.put(CardId.DIMENSION, new DimensionCard(context).hasData());
        m.put(CardId.SOCIAL, context.serverTicks() >= SOCIAL_MIN_SERVER_TICKS
                || context.playersMet() > 0 || context.messagesSent() > 0);
        m.put(CardId.DISTANCE, totalDistanceCm(context) >= 100_000L);
        m.put(CardId.TOP_BLOCKS, !context.topMined(1).isEmpty());
        m.put(CardId.TOP_MOB, !context.topKilled(1).isEmpty());
        m.put(CardId.CRAFTING, context.delta().total("minecraft:crafted") > 0);
        m.put(CardId.DEATH_RECAP, context.playTimeTicks() >= DEATHS_MIN_PLAY_TICKS);
        m.put(CardId.ARCHETYPE, context.playTimeTicks() >= MIN_PLAY_TICKS);
        m.put(CardId.FINAL, true);
        return m;
    }

    private static long totalDistanceCm(final WrappedContext ctx) {
        final Map<String, Long> custom = ctx.delta().deltas().getOrDefault("minecraft:custom", Map.of());
        return custom.getOrDefault("minecraft:walk_one_cm", 0L)
                + custom.getOrDefault("minecraft:sprint_one_cm", 0L)
                + custom.getOrDefault("minecraft:boat_one_cm", 0L)
                + custom.getOrDefault("minecraft:aviate_one_cm", 0L)
                + custom.getOrDefault("minecraft:horse_one_cm", 0L)
                + custom.getOrDefault("minecraft:fly_one_cm", 0L)
                + custom.getOrDefault("minecraft:swim_one_cm", 0L);
    }
}
