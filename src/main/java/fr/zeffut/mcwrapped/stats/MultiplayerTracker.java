package fr.zeffut.mcwrapped.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks multiplayer-only signals the client can collect:
 * <ul>
 *   <li>Unique players seen across server sessions (by UUID)</li>
 *   <li>Chat messages sent</li>
 *   <li>Slash commands run</li>
 *   <li>Distinct servers visited</li>
 * </ul>
 *
 * <p>Persisted to {@code <gameDir>/wrapped/multiplayer-data.json}.
 */
public final class MultiplayerTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int SAVE_INTERVAL_TICKS = 1200;

    private final Path file;
    private final Set<UUID> playersSeen = new LinkedHashSet<>();
    private final Set<String> serversVisited = new LinkedHashSet<>();
    private long messagesSent = 0;
    private long commandsSent = 0;

    private int pollCounter = 0;
    private int saveCounter = 0;
    private UUID selfUuid;

    public MultiplayerTracker() {
        this.file = FabricLoader.getInstance().getGameDir().resolve("wrapped").resolve("multiplayer-data.json");
        load();
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> noteServer(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> save());
        ClientSendMessageEvents.CHAT.register(message -> { messagesSent++; });
        ClientSendMessageEvents.COMMAND.register(command -> { commandsSent++; });
    }

    public Set<UUID> playersSeen() { return playersSeen; }
    public int playersSeenCount() { return playersSeen.size(); }
    public long messagesSent() { return messagesSent; }
    public long commandsSent() { return commandsSent; }
    public int serversVisitedCount() { return serversVisited.size(); }

    private void onTick(final Minecraft client) {
        if (client.world == null || client.isInSingleplayer() || client.getNetworkHandler() == null) return;
        if (selfUuid == null) selfUuid = client.getSession().getUuidOrNull();

        if (++pollCounter >= POLL_INTERVAL_TICKS) {
            pollCounter = 0;
            for (final PlayerInfo entry : client.getNetworkHandler().getPlayerList()) {
                final UUID uuid = entry.getProfile().id();
                if (uuid != null && !uuid.equals(selfUuid)) {
                    playersSeen.add(uuid);
                }
            }
        }

        if (++saveCounter >= SAVE_INTERVAL_TICKS) {
            saveCounter = 0;
            save();
        }
    }

    private void noteServer(final Minecraft client) {
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null) {
            serversVisited.add(client.getCurrentServerEntry().address.toLowerCase());
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.has("players_seen")) {
                for (final JsonElement el : root.getAsJsonArray("players_seen")) {
                    try { playersSeen.add(UUID.fromString(el.getAsString())); } catch (final IllegalArgumentException ignore) {}
                }
            }
            if (root.has("servers_visited")) {
                for (final JsonElement el : root.getAsJsonArray("servers_visited")) {
                    serversVisited.add(el.getAsString());
                }
            }
            if (root.has("messages_sent")) messagesSent = root.get("messages_sent").getAsLong();
            if (root.has("commands_sent")) commandsSent = root.get("commands_sent").getAsLong();
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load multiplayer data: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            final JsonObject root = new JsonObject();
            final JsonArray players = new JsonArray();
            for (final UUID u : playersSeen) players.add(u.toString());
            root.add("players_seen", players);
            final JsonArray servers = new JsonArray();
            for (final String s : serversVisited) servers.add(s);
            root.add("servers_visited", servers);
            root.addProperty("messages_sent", messagesSent);
            root.addProperty("commands_sent", commandsSent);
            Files.writeString(file, GSON.toJson(root));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save multiplayer data: {}", e.getMessage());
        }
    }
}
