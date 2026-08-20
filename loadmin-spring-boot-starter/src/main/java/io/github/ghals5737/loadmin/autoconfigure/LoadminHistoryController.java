package io.github.ghals5737.loadmin.autoconfigure;

import java.util.List;

import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.LoadTestRunRegistry;
import io.github.ghals5737.loadmin.core.history.HistoryEntry;
import io.github.ghals5737.loadmin.core.history.RunHistoryStore;
import io.github.ghals5737.loadmin.core.engine.RunView;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access to past runs: the history list, one stored run, and the baseline
 * a run should be compared against.
 *
 * <p>Registered only when history is enabled, so the UI treats a 404 here as
 * "history is off" and hides the section.
 */
@RestController
public class LoadminHistoryController {

    private final RunHistoryStore history;
    private final LoadTestRunRegistry runs;

    public LoadminHistoryController(RunHistoryStore history, LoadTestRunRegistry runs) {
        this.history = history;
        this.runs = runs;
    }

    @GetMapping("/loadmin/api/history")
    public List<HistoryEntry> list() {
        return history.list();
    }

    @GetMapping("/loadmin/api/history/{id}")
    public ResponseEntity<RunView> get(@PathVariable String id) {
        RunView view = history.load(id);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /**
     * The previous completed run of the same endpoint, or 204 when this run is
     * the first of its kind. Reads the live run from the registry so it works
     * the moment a run finishes, before its own file lands.
     */
    @GetMapping("/loadmin/api/runs/{id}/baseline")
    public ResponseEntity<RunView> baseline(@PathVariable String id) {
        LoadTestRun run = runs.get(id);
        RunView view = run != null ? run.view() : history.load(id);
        if (view == null) {
            return ResponseEntity.notFound().build();
        }
        RunView baseline = history.baselineFor(view.id(), view.spec(), view.startedAtMillis());
        return baseline == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(baseline);
    }
}
