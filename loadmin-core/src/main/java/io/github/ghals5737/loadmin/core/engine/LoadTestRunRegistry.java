package io.github.ghals5737.loadmin.core.engine;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory registry of runs. Keeps only the most recent runs to bound memory;
 * persistence/history comparison is a later phase.
 */
public class LoadTestRunRegistry {

    private static final int MAX_RUNS = 20;

    private final ConcurrentMap<String, LoadTestRun> runs = new ConcurrentHashMap<>();

    public void add(LoadTestRun run) {
        runs.put(run.id(), run);
        evictOldest();
    }

    public LoadTestRun get(String id) {
        return runs.get(id);
    }

    public List<LoadTestRun> all() {
        return runs.values().stream()
                .sorted(Comparator.comparingLong(LoadTestRun::startedAtMillis).reversed())
                .toList();
    }

    private void evictOldest() {
        while (runs.size() > MAX_RUNS) {
            runs.values().stream()
                    .filter(r -> r.status() != RunStatus.RUNNING)
                    .min(Comparator.comparingLong(LoadTestRun::startedAtMillis))
                    .ifPresentOrElse(r -> runs.remove(r.id()), () -> {
                    });
            break;
        }
    }
}
