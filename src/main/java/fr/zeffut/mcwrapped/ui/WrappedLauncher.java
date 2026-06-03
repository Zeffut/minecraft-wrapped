package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.archetype.Archetype;
import fr.zeffut.mcwrapped.telemetry.Events;
import fr.zeffut.mcwrapped.telemetry.StatsBucketer;
import fr.zeffut.mcwrapped.telemetry.Telemetry;
import fr.zeffut.mcwrapped.ui.cards.Card;
import fr.zeffut.mcwrapped.ui.cards.WrappedContext;
import fr.zeffut.mcwrapped.ui.cards.WrappedSequence;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single entry point to open a Wrapped experience. Centralizes the telemetry fired when a wrapped
 * is launched so every caller (command, title button, ...) records consistent events.
 */
public final class WrappedLauncher {

    private WrappedLauncher() {}

    /**
     * Opens the wrapped card screen for {@code ctx}, emitting trigger / started / generated events.
     *
     * @param source where the launch came from: "command", "title_button", ...
     */
    public static void open(@Nullable final Screen parent, final WrappedContext ctx, final String source) {
        Telemetry.capture(Events.WRAPPED_TRIGGERED, Map.of("source", source));

        final List<Card> cards = WrappedSequence.full(ctx);

        Telemetry.capture(Events.WRAPPED_STARTED, Map.of(
                "month", ctx.month().toString(),
                "card_count", cards.size()));

        final Map<String, Object> gen = new HashMap<>();
        gen.put("archetype", Archetype.pick(ctx).name());
        gen.put("playtime_bucket", StatsBucketer.playtimeBucket(ctx.playTimeTicks()));
        gen.put("deaths_bucket", StatsBucketer.deathsBucket(ctx.deaths()));
        gen.put("distance_bucket", StatsBucketer.distanceBucket(ctx.totalDistanceCm()));
        gen.put("context_type", ctx.contextType());
        gen.put("cards_shown", cards.size());
        Telemetry.capture(Events.WRAPPED_GENERATED, gen);

        MinecraftClient.getInstance().setScreen(new WrappedCardScreen(parent, cards));
    }
}
