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

/**
 * Persists a single global snapshot per month at {@code <gameDir>/wrapped/snapshot-YYYY-MM.json}.
 *
 * <p>The Wrapped concept covers the player's whole game, not a specific world or server — so we
 * keep a single snapshot lineage rather than per-context buckets.
 */
public final class SnapshotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String FILE_PREFIX = "snapshot-";
    private static final String FILE_SUFFIX = ".json";

    private final Path root;

    public SnapshotManager() {
        this(FabricLoader.getInstance().getGameDir().resolve("wrapped"));
    }

    public SnapshotManager(final Path root) {
        this.root = root;
    }

    public Path snapshotPath(final YearMonth month) {
        return root.resolve(FILE_PREFIX + month.format(MONTH_FMT) + FILE_SUFFIX);
    }

    public void save(final StatsSnapshot snapshot) {
        try {
            Files.createDirectories(root);
            final JsonObject obj = new JsonObject();
            obj.addProperty("month", snapshot.month().format(MONTH_FMT));
            obj.addProperty("captured_at", snapshot.capturedAt().toString());
            obj.add("stats_raw", GSON.toJsonTree(snapshot.statsRaw()));
            Files.writeString(snapshotPath(snapshot.month()), GSON.toJson(obj));
            McWrappedClient.LOGGER.info("Saved snapshot for {}", snapshot.month());
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to save snapshot: {}", e.getMessage());
        }
    }

    public Optional<StatsSnapshot> load(final YearMonth month) {
        final Path file = snapshotPath(month);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            final JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            final YearMonth m = YearMonth.parse(obj.get("month").getAsString(), MONTH_FMT);
            final Instant ts = Instant.parse(obj.get("captured_at").getAsString());
            final Map<String, Map<String, Long>> raw = parseRaw(obj.getAsJsonObject("stats_raw"));
            return Optional.of(new StatsSnapshot(m, ts, raw));
        } catch (final IOException e) {
            McWrappedClient.LOGGER.warn("Failed to load snapshot {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<StatsSnapshot> loadLatest() {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (final Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(p -> {
                        final String name = p.getFileName().toString();
                        return name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX);
                    })
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .flatMap(p -> {
                        final String name = p.getFileName().toString();
                        final String monthStr = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
                        return load(YearMonth.parse(monthStr, MONTH_FMT));
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
