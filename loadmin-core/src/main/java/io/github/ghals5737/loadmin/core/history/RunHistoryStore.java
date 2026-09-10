package io.github.ghals5737.loadmin.core.history;

import static java.lang.System.Logger.Level.WARNING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;
import io.github.ghals5737.loadmin.core.engine.RunStatus;
import io.github.ghals5737.loadmin.core.engine.RunView;

/**
 * Keeps finished runs as JSON files so results survive a restart and a run can
 * be compared with the last one that hit the same endpoint.
 *
 * <p>One file per run, named {@code <startedAtMillis>-<id>.json}, so the
 * directory sorts by time and a run can be found by id without an index. Writes
 * go to a temporary file and are moved into place, so a crash mid-write cannot
 * leave a half-parsed run behind.
 *
 * <p>History is a convenience, never a reason for a load test to fail: every
 * failure here is logged and swallowed.
 */
public class RunHistoryStore {

    private static final System.Logger LOG = System.getLogger(RunHistoryStore.class.getName());
    private static final String SUFFIX = ".json";

    private final Path directory;
    private final int maxRuns;
    private final ObjectMapper mapper;

    public RunHistoryStore(Path directory, int maxRuns, ObjectMapper mapper) {
        this.directory = directory;
        this.maxRuns = Math.max(1, maxRuns);
        this.mapper = mapper;
    }

    /** Where runs are written, for logging and for the UI to show. */
    public Path directory() {
        return directory.toAbsolutePath();
    }

    public void save(RunView view) {
        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, "run-", ".tmp");
            mapper.writeValue(temp.toFile(), view);
            Files.move(temp, directory.resolve(view.startedAtMillis() + "-" + view.id() + SUFFIX),
                    StandardCopyOption.REPLACE_EXISTING);
            evictOldest();
        } catch (Exception e) {
            LOG.log(WARNING, "loadmin: could not save run " + view.id() + " to " + directory(), e);
            deleteQuietly(temp);
        }
    }

    /** Past runs, newest first. */
    public List<HistoryEntry> list() {
        List<HistoryEntry> entries = new ArrayList<>();
        for (Path file : files()) {
            RunView view = read(file);
            if (view != null) {
                entries.add(HistoryEntry.of(view));
            }
        }
        entries.sort(Comparator.comparingLong(HistoryEntry::startedAtMillis).reversed());
        return entries;
    }

    /** The stored run, or {@code null} if it was never saved or has been evicted. */
    public RunView load(String id) {
        for (Path file : files()) {
            if (file.getFileName().toString().endsWith("-" + id + SUFFIX)) {
                return read(file);
            }
        }
        return null;
    }

    /**
     * The run to compare against: the most recent completed run of the same
     * endpoint <em>under the same load</em> that started before this one.
     * {@code null} when there is nothing comparable yet.
     *
     * <p>The load has to match. A run with 40 concurrent users next to one with
     * 5 shows a much worse p95, and presenting that as a delta invites reading
     * a heavier test as a regression. Runs at different loads can still be
     * compared explicitly from the history list, where both loads are on screen.
     */
    public RunView baselineFor(String runId, LoadTestSpec spec, long startedAtMillis) {
        HistoryEntry baseline = list().stream()
                .filter(entry -> !entry.id().equals(runId))
                .filter(entry -> entry.startedAtMillis() < startedAtMillis)
                .filter(entry -> entry.status() == RunStatus.COMPLETED)
                .filter(entry -> entry.summary().requests() > 0)
                .filter(entry -> entry.sameTarget(spec))
                .filter(entry -> entry.sameLoad(spec))
                .max(Comparator.comparingLong(HistoryEntry::startedAtMillis))
                .orElse(null);
        return baseline == null ? null : load(baseline.id());
    }

    private List<Path> files() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOG.log(WARNING, "loadmin: could not read history from " + directory(), e);
            return List.of();
        }
    }

    private RunView read(Path file) {
        try {
            return mapper.readValue(file.toFile(), RunView.class);
        } catch (IOException e) {
            // A file written by an older version, or a partial write: skip it
            // rather than breaking the whole history list.
            LOG.log(WARNING, "loadmin: skipping unreadable history file " + file, e);
            return null;
        }
    }

    /** File names start with the start time, so the oldest sort first. */
    private void evictOldest() {
        List<Path> files = files();
        for (int i = 0; i < files.size() - maxRuns; i++) {
            deleteQuietly(files.get(i));
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException | UncheckedIOException e) {
            LOG.log(WARNING, "loadmin: could not delete " + file, e);
        }
    }
}
