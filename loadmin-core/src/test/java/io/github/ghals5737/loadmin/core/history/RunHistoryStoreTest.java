package io.github.ghals5737.loadmin.core.history;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;
import io.github.ghals5737.loadmin.core.engine.RunStatus;
import io.github.ghals5737.loadmin.core.engine.RunView;
import io.github.ghals5737.loadmin.core.metrics.ServerMetricsSample;
import io.github.ghals5737.loadmin.core.query.SlowQuery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunHistoryStoreTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void savedRunSurvivesARoundTrip(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        RunView saved = run("abc", 1000, RunStatus.COMPLETED, "GET", "/api/users/{id}", 42);

        store.save(saved);

        assertEquals(saved, store.load("abc"));
    }

    @Test
    void listIsNewestFirst(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        store.save(run("old", 1000, RunStatus.COMPLETED, "GET", "/api/hello", 10));
        store.save(run("new", 3000, RunStatus.COMPLETED, "GET", "/api/hello", 20));
        store.save(run("mid", 2000, RunStatus.COMPLETED, "GET", "/api/hello", 30));

        assertEquals(List.of("new", "mid", "old"), store.list().stream().map(HistoryEntry::id).toList());
    }

    @Test
    void entryCarriesTheSummaryButNotTheTimeline(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        store.save(run("abc", 1000, RunStatus.COMPLETED, "GET", "/api/hello", 42));

        HistoryEntry entry = store.list().get(0);

        assertEquals(42, entry.summary().p95());
        assertEquals("/api/hello", entry.spec().pathPattern());
        assertEquals(RunStatus.COMPLETED, entry.status());
    }

    @Test
    void oldestRunsAreEvictedBeyondTheLimit(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 3, mapper);
        for (int i = 1; i <= 6; i++) {
            store.save(run("run" + i, i * 1000L, RunStatus.COMPLETED, "GET", "/api/hello", i));
        }

        assertEquals(List.of("run6", "run5", "run4"), store.list().stream().map(HistoryEntry::id).toList());
    }

    @Test
    void baselineIsTheLastCompletedRunOfTheSameEndpoint(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        store.save(run("older", 1000, RunStatus.COMPLETED, "GET", "/api/users/{id}", 30));
        store.save(run("wanted", 2000, RunStatus.COMPLETED, "GET", "/api/users/{id}", 40));
        store.save(run("otherEndpoint", 2500, RunStatus.COMPLETED, "GET", "/api/hello", 50));
        store.save(run("failed", 2600, RunStatus.FAILED, "GET", "/api/users/{id}", 60));
        store.save(run("later", 9000, RunStatus.COMPLETED, "GET", "/api/users/{id}", 70));

        RunView current = run("current", 3000, RunStatus.COMPLETED, "GET", "/api/users/{id}", 80);
        store.save(current);

        RunView baseline = store.baselineFor(current.id(), current.spec(), current.startedAtMillis());

        assertNotNull(baseline);
        assertEquals("wanted", baseline.id());
    }

    @Test
    void differentPathTemplatesOnTheSameEndpointStillCompare(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        store.save(new RunView("previous", RunStatus.COMPLETED, null,
                new LoadTestSpec("GET", "/api/users/{id}", "/api/users/7", null, 10, 15),
                1000, 15, summary(30), List.of(), List.of(), List.of()));

        LoadTestSpec now = new LoadTestSpec("GET", "/api/users/{id}", "/api/users/${int(1,20)}", null, 10, 15);
        RunView baseline = store.baselineFor("current", now, 2000);

        assertNotNull(baseline);
        assertEquals("previous", baseline.id());
    }

    @Test
    void aRunAtADifferentLoadIsNotABaseline(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        store.save(run("light", 1000, RunStatus.COMPLETED, "GET", "/api/users/{id}", 30, 5));
        store.save(run("heavy", 2000, RunStatus.COMPLETED, "GET", "/api/users/{id}", 90, 40));

        LoadTestSpec at40 = new LoadTestSpec("GET", "/api/users/{id}", "/api/users/1", null, 40, 15);
        LoadTestSpec at5 = new LoadTestSpec("GET", "/api/users/{id}", "/api/users/1", null, 5, 15);

        // Latency is a function of load, so only the run at the same load counts.
        assertEquals("heavy", store.baselineFor("current", at40, 3000).id());
        assertEquals("light", store.baselineFor("current", at5, 3000).id());
        assertNull(store.baselineFor("current",
                new LoadTestSpec("GET", "/api/users/{id}", "/api/users/1", null, 99, 15), 3000));
    }

    @Test
    void noBaselineWhenNothingComparableExists(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        store.save(run("other", 1000, RunStatus.COMPLETED, "POST", "/api/orders", 30));

        LoadTestSpec spec = new LoadTestSpec("GET", "/api/hello", "/api/hello", null, 10, 15);

        assertNull(store.baselineFor("current", spec, 2000));
    }

    @Test
    void missingDirectoryReadsAsEmptyHistory(@TempDir Path dir) {
        RunHistoryStore store = new RunHistoryStore(dir.resolve("not-created-yet"), 10, mapper);

        assertEquals(List.of(), store.list());
        assertNull(store.load("whatever"));
    }

    @Test
    void anUnreadableFileDoesNotBreakTheList(@TempDir Path dir) throws Exception {
        RunHistoryStore store = new RunHistoryStore(dir, 10, mapper);
        store.save(run("good", 2000, RunStatus.COMPLETED, "GET", "/api/hello", 10));
        Files.writeString(dir.resolve("1000-broken.json"), "{ this is not json");

        List<HistoryEntry> entries = store.list();

        assertEquals(1, entries.size());
        assertEquals("good", entries.get(0).id());
    }

    @Test
    void aFailingSaveIsSwallowed(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("occupied"), "not a directory");
        RunHistoryStore store = new RunHistoryStore(file, 10, mapper);

        assertDoesNotThrow(() -> store.save(run("abc", 1000, RunStatus.COMPLETED, "GET", "/api/hello", 10)));
        assertTrue(store.list().isEmpty());
    }

    private static RunView run(String id, long startedAt, RunStatus status,
            String method, String pattern, long p95) {
        return run(id, startedAt, status, method, pattern, p95, 10);
    }

    private static RunView run(String id, long startedAt, RunStatus status,
            String method, String pattern, long p95, int concurrency) {
        return new RunView(id, status, null,
                new LoadTestSpec(method, pattern, pattern, null, concurrency, 15),
                startedAt, 15, summary(p95),
                List.of(new RunView.TimelinePoint(0, 100, 0, 20, p95)),
                List.of(new ServerMetricsSample(0, Map.of("tomcatThreadsBusy", 4.0))),
                List.of(new SlowQuery("select 1", 2, 120, 200)));
    }

    private static RunView.Summary summary(long p95) {
        return new RunView.Summary(1000, 5, 0.005, 66.6, 20, p95, p95 + 10, p95 + 40);
    }
}
