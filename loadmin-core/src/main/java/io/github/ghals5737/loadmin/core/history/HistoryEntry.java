package io.github.ghals5737.loadmin.core.history;

import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;
import io.github.ghals5737.loadmin.core.engine.RunStatus;
import io.github.ghals5737.loadmin.core.engine.RunView;

/**
 * One past run as it appears in the history list: everything needed for a row
 * and a comparison, without the per-second timeline.
 *
 * @param id              run id, also the key to load the full {@link RunView}
 * @param startedAtMillis wall-clock start, used to order runs
 * @param status          how the run ended
 * @param spec            what was tested and with which load
 * @param summary         the run's overall numbers
 */
public record HistoryEntry(String id, long startedAtMillis, RunStatus status,
        LoadTestSpec spec, RunView.Summary summary) {

    public static HistoryEntry of(RunView view) {
        return new HistoryEntry(view.id(), view.startedAtMillis(), view.status(),
                view.spec(), view.summary());
    }

    /**
     * Whether this run hit the same endpoint as {@code other} — the condition
     * for two runs to be comparable at all. The path template may differ (a
     * wider {@code ${int(...)}} range is still the same endpoint); the mapping
     * pattern and method are what must match.
     */
    public boolean sameTarget(LoadTestSpec other) {
        return spec.httpMethod().equals(other.httpMethod())
                && spec.pathPattern().equals(other.pathPattern());
    }
}
