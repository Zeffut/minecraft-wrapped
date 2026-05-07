package fr.zeffut.mcwrapped.ui.cards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WrappedSequence {

    private static final long MIN_PLAY_TICKS = 20L * 60;
    private static final long DEATHS_MIN_PLAY_TICKS = 20L * 60 * 30;
    private static final long SOCIAL_MIN_SERVER_TICKS = 20L * 60 * 5;

    private WrappedSequence() {}

    public static List<Card> full(final WrappedContext context) {
        final List<Card> cards = new ArrayList<>();
        cards.add(new IntroCard(context.month()));

        if (context.playTimeTicks() >= MIN_PLAY_TICKS) {
            cards.add(new TimeSpentCard(context));
        }

        // Bonus: session info, only if we have at least one session in the month.
        final LongestSessionCard sessionCard = new LongestSessionCard(context);
        if (sessionCard.hasData()) cards.add(sessionCard);

        // Bonus: time-of-day distribution.
        final TimeOfDayCard timeOfDay = new TimeOfDayCard(context);
        if (timeOfDay.hasData()) cards.add(timeOfDay);

        if (!context.topWorlds(1).isEmpty()) {
            cards.add(new TopWorldCard(context));
        }

        // Bonus: dimensions explored.
        final DimensionCard dimensionCard = new DimensionCard(context);
        if (dimensionCard.hasData()) cards.add(dimensionCard);

        if (context.serverTicks() >= SOCIAL_MIN_SERVER_TICKS || context.playersMet() > 0 || context.messagesSent() > 0) {
            cards.add(new SocialCard(context));
        }
        if (totalDistanceCm(context) >= 100_000L) {
            cards.add(new DistanceCard(context));
        }
        if (!context.topMined(1).isEmpty()) {
            cards.add(new TopBlocksCard(context));
        }
        if (!context.topKilled(1).isEmpty()) {
            cards.add(new TopMobCard(context));
        }
        if (context.delta().total("minecraft:crafted") > 0) {
            cards.add(new CraftingCard(context));
        }
        if (context.playTimeTicks() >= DEATHS_MIN_PLAY_TICKS) {
            cards.add(new DeathRecapCard(context));
        }
        if (context.playTimeTicks() >= MIN_PLAY_TICKS) {
            cards.add(new ArchetypeCard(context));
        }
        cards.add(new FinalCard(context));

        return cards;
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
