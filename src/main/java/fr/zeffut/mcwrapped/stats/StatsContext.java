package fr.zeffut.mcwrapped.stats;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

public record StatsContext(String id, Kind kind) {

    public enum Kind {
        SINGLEPLAYER,
        SERVER
    }

    public static Optional<StatsContext> current(final MinecraftClient client) {
        if (client.isInSingleplayer() && client.getServer() != null) {
            final String worldName = client.getServer().getSaveProperties().getLevelName();
            return Optional.of(new StatsContext("singleplayer-" + sanitize(worldName), Kind.SINGLEPLAYER));
        }
        final ServerInfo info = client.getCurrentServerEntry();
        if (info != null) {
            return Optional.of(new StatsContext("server-" + shortHash(info.address), Kind.SERVER));
        }
        return Optional.empty();
    }

    private static String sanitize(final String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String shortHash(final String input) {
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            final byte[] digest = sha1.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
