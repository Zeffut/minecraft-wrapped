package fr.zeffut.mcwrapped.ui.cards;

import java.util.ArrayList;
import java.util.List;

public final class WrappedSequence {

    private static final long MIN_PLAY_TICKS = 20L * 60;
    private static final long DEATHS_MIN_PLAY_TICKS = 20L * 60 * 30;
    private static final long SOCIAL_MIN_SERVER_TICKS = 20L * 60 * 5; // 5 min on a server to be social

    private WrappedSequence() {}

    public static List<Card> full(final WrappedContext context) {
        final List<Card> cards = new ArrayList<>();
        cards.add(new IntroCard(context.month()));

        if (context.playTimeTicks() >= MIN_PLAY_TICKS) {
            cards.add(new TimeSpentCard(context));
        }
        if (!context.topWorlds(1).isEmpty()) {
            cards.add(new TopWorldCard(context));
        }
        if (context.serverTicks() >= SOCIAL_MIN_SERVER_TICKS || context.playersMet() > 0 || context.messagesSent() > 0) {
            cards.add(new SocialCard(context));
        }
        if (!context.topMined(1).isEmpty()) {
            cards.add(new TopBlocksCard(context));
        }
        if (!context.topKilled(1).isEmpty()) {
            cards.add(new TopMobCard(context));
        }
        if (context.playTimeTicks() >= DEATHS_MIN_PLAY_TICKS) {
            cards.add(new DeathRecapCard(context));
        }
        cards.add(new FinalCard(context));

        return cards;
    }
}
