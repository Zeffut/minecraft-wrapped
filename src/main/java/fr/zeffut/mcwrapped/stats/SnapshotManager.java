package fr.zeffut.mcwrapped.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.mcwrapped.McWrappedClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class SnapshotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String SNAPSHOT_PREFIX = "snapshot-";
    private static final String WRAPPED_PREFIX = "wrapped-";
    private static final String SUFFIX = ".json";

    private final Path root;

    public SnapshotManager() {
        this(FabricLoader.getInstance().getGameDir().resolve("wrapped"));
    }

    public SnapshotManager(final Path root) {
        this.root = root;
    }

    // --- Snapshots ----------------------------------------------------------

    public Path snapshotPath(final YearMonth month) {
        return root.resolve(SNAPSHOT_PREFIX + month.format(MONTH_FMT) + SUFFIX);
    }

    public void saveSnapshot(final StatsSnapshot snapshot) {
        try {
            Files.createDirectories(root);
            final JsonObject obj = new JsonObject();
            obj.addProperty("month", snapshot.month().format(MONTH_FMT));
            obj.addProperty("captured_at", snapshot.capturedAt().toString());
            obj.add("stats_raw", GSON.toJsonTree(snapshot.statsRaw()));
            obj.add("per_world_play_time", GSON.toJsonTree(snapshot.perWorldPlayTime()));
            obj.addProperty("players_seen_count", snapshot.playersSeenCount());
            obj.addProperty("messages_sent", snapshot.messagesSent());
            obj.addProperty("commands_sent", snapshot.commandsSent());
            obj.addProperty("servers_visited_count", snapshot.serversVisitedCount());
            Files.writeString(snapshotPath(snapshot.month()), GSON.toJson(obj));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save snapshot: {}", e.getMessage());
        }
    }

    public Optional<StatsSnapshot> loadSnapshot(final YearMonth month) {
        return loadSnapshotFile(snapshotPath(month));
    }

    public Optional<StatsSnapshot> loadLatestSnapshot() {
        return latestFile(SNAPSHOT_PREFIX).flatMap(this::loadSnapshotFile);
    }

    private Optional<StatsSnapshot> loadSnapshotFile(final Path file) {
        if (!Files.exists(file)) return Optional.empty();
        try {
            final JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            final YearMonth m = YearMonth.parse(obj.get("month").getAsString(), MONTH_FMT);
            final Instant ts = Instant.parse(obj.get("captured_at").getAsString());
            final Map<String, Map<String, Long>> raw = parseStatsRaw(obj.getAsJsonObject("stats_raw"));
            final Map<String, Long> perWorld = parseLongMap(obj.has("per_world_play_time") ? obj.getAsJsonObject("per_world_play_time") : null);
            final int playersSeen = obj.has("players_seen_count") ? obj.get("players_seen_count").getAsInt() : 0;
            final long messages = obj.has("messages_sent") ? obj.get("messages_sent").getAsLong() : 0L;
            final long commands = obj.has("commands_sent") ? obj.get("commands_sent").getAsLong() : 0L;
            final int serversVisited = obj.has("servers_visited_count") ? obj.get("servers_visited_count").getAsInt() : 0;
            return Optional.of(new StatsSnapshot(m, ts, raw, perWorld, playersSeen, messages, commands, serversVisited));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load snapshot {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    // --- Finalized wrapped --------------------------------------------------

    public Path wrappedPath(final YearMonth month) {
        return root.resolve(WRAPPED_PREFIX + month.format(MONTH_FMT) + SUFFIX);
    }

    public boolean wrappedExists(final YearMonth month) {
        return Files.exists(wrappedPath(month));
    }

    public void saveWrapped(final WrappedFile wrapped) {
        try {
            Files.createDirectories(root);
            final JsonObject obj = new JsonObject();
            obj.addProperty("month", wrapped.month().format(MONTH_FMT));
            obj.addProperty("consumed", wrapped.consumed());
            obj.add("deltas", GSON.toJsonTree(wrapped.delta().deltas()));
            obj.add("per_world_play_time_delta", GSON.toJsonTree(wrapped.delta().perWorldPlayTimeDelta()));
            obj.addProperty("players_met_delta", wrapped.delta().playersMetDelta());
            obj.addProperty("messages_sent_delta", wrapped.delta().messagesSentDelta());
            obj.addProperty("commands_sent_delta", wrapped.delta().commandsSentDelta());
            obj.addProperty("servers_visited_delta", wrapped.delta().serversVisitedDelta());
            Files.writeString(wrappedPath(wrapped.month()), GSON.toJson(obj));
            McWrappedClient.LOGGER.info("Wrapped finalized for {}.", wrapped.month());
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save wrapped: {}", e.getMessage());
        }
    }

    public Optional<WrappedFile> loadWrapped(final YearMonth month) {
        return loadWrappedFile(wrappedPath(month));
    }

    public List<WrappedFile> listWrapped() {
        if (!Files.isDirectory(root)) return List.of();
        try (final Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(p -> {
                        final String n = p.getFileName().toString();
                        return n.startsWith(WRAPPED_PREFIX) && n.endsWith(SUFFIX);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(this::loadWrappedFile)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (final IOException e) {
            return List.of();
        }
    }

    public Optional<WrappedFile> findLatestUnconsumed() {
        return listWrapped().stream()
                .filter(w -> !w.consumed())
                .max(Comparator.comparing(WrappedFile::month));
    }

    private Optional<WrappedFile> loadWrappedFile(final Path file) {
        if (!Files.exists(file)) return Optional.empty();
        try {
            final JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            final YearMonth m = YearMonth.parse(obj.get("month").getAsString(), MONTH_FMT);
            final boolean consumed = obj.has("consumed") && obj.get("consumed").getAsBoolean();
            final Map<String, Map<String, Long>> deltas = parseStatsRaw(obj.getAsJsonObject("deltas"));
            final Map<String, Long> perWorld = parseLongMap(obj.has("per_world_play_time_delta") ? obj.getAsJsonObject("per_world_play_time_delta") : null);
            final int playersMet = obj.has("players_met_delta") ? obj.get("players_met_delta").getAsInt() : 0;
            final long messages = obj.has("messages_sent_delta") ? obj.get("messages_sent_delta").getAsLong() : 0L;
            final long commands = obj.has("commands_sent_delta") ? obj.get("commands_sent_delta").getAsLong() : 0L;
            final int serversVisited = obj.has("servers_visited_delta") ? obj.get("servers_visited_delta").getAsInt() : 0;
            final MonthlyDelta delta = new MonthlyDelta(m, deltas, perWorld, playersMet, messages, commands, serversVisited);
            return Optional.of(new WrappedFile(m, delta, consumed));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load wrapped {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    // --- Helpers ------------------------------------------------------------

    private Optional<Path> latestFile(final String prefix) {
        if (!Files.isDirectory(root)) return Optional.empty();
        try (final Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(p -> {
                        final String n = p.getFileName().toString();
                        return n.startsWith(prefix) && n.endsWith(SUFFIX);
                    })
                    .max(Comparator.comparing(p -> p.getFileName().toString()));
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private static Map<String, Map<String, Long>> parseStatsRaw(final JsonObject obj) {
        final Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        if (obj == null) return out;
        for (final var category : obj.entrySet()) {
            final Map<String, Long> values = new LinkedHashMap<>();
            for (final var stat : category.getValue().getAsJsonObject().entrySet()) {
                values.put(stat.getKey(), stat.getValue().getAsLong());
            }
            out.put(category.getKey(), values);
        }
        return out;
    }

    private static Map<String, Long> parseLongMap(final JsonObject obj) {
        final Map<String, Long> out = new LinkedHashMap<>();
        if (obj == null) return out;
        for (final var entry : obj.entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsLong());
        }
        return out;
    }
}
