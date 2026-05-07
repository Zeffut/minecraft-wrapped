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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class SnapshotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Path root;

    public SnapshotManager() {
        this(FabricLoader.getInstance().getGameDir().resolve("wrapped"));
    }

    public SnapshotManager(final Path root) {
        this.root = root;
    }

    public Path contextDir(final StatsContext context) {
        return root.resolve(context.id());
    }

    public Path snapshotPath(final StatsContext context, final YearMonth month) {
        return contextDir(context).resolve("snapshot-" + month.format(MONTH_FMT) + ".json");
    }

    public void save(final StatsContext context, final StatsSnapshot snapshot) {
        try {
            Files.createDirectories(contextDir(context));
            final JsonObject root = new JsonObject();
            root.addProperty("month", snapshot.month().format(MONTH_FMT));
            root.addProperty("context", snapshot.contextId());
            root.addProperty("captured_at", snapshot.capturedAt().toString());
            root.add("stats_raw", GSON.toJsonTree(snapshot.statsRaw()));
            Files.writeString(snapshotPath(context, snapshot.month()), GSON.toJson(root));
            McWrappedClient.LOGGER.info("Saved snapshot {} for context {}", snapshot.month(), context.id());
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save snapshot: {}", e.getMessage());
        }
    }

    public Optional<StatsSnapshot> load(final StatsContext context, final YearMonth month) {
        final Path file = snapshotPath(context, month);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            final JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            final YearMonth m = YearMonth.parse(obj.get("month").getAsString(), MONTH_FMT);
            final String ctx = obj.get("context").getAsString();
            final Instant ts = Instant.parse(obj.get("captured_at").getAsString());
            final Map<String, Map<String, Long>> raw = parseRaw(obj.getAsJsonObject("stats_raw"));
            return Optional.of(new StatsSnapshot(m, ctx, ts, raw));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load snapshot {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<StatsSnapshot> loadLatest(final StatsContext context) {
        final Path dir = contextDir(context);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("snapshot-"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .flatMap(p -> {
                        final String name = p.getFileName().toString();
                        final String monthStr = name.substring("snapshot-".length(), name.length() - ".json".length());
                        return load(context, YearMonth.parse(monthStr, MONTH_FMT));
                    });
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private static Map<String, Map<String, Long>> parseRaw(final JsonObject obj) {
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
}
