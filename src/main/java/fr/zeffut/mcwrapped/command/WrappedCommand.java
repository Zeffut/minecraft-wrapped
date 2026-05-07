package fr.zeffut.mcwrapped.command;

import com.mojang.brigadier.Command;
import fr.zeffut.mcwrapped.ui.WrappedCardScreen;
import fr.zeffut.mcwrapped.ui.cards.IntroCard;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.time.YearMonth;
import java.time.ZoneId;

public final class WrappedCommand {

    private WrappedCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("wrapped")
                    .then(ClientCommandManager.literal("test")
                            .then(ClientCommandManager.literal("intro").executes(ctx -> {
                                openIntro();
                                ctx.getSource().sendFeedback(Text.literal("Wrapped: opening intro card"));
                                return Command.SINGLE_SUCCESS;
                            }))));
        });
    }

    private static void openIntro() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final YearMonth month = YearMonth.now(ZoneId.systemDefault()).minusMonths(1);
        client.send(() -> client.setScreen(new WrappedCardScreen(null, new IntroCard(month))));
    }
}
