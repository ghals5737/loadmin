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
     * Whether this run walked the same endpoints in the same order as
     * {@code other}. Path templates may differ (a wider {@code ${int(...)}}
     * range is still the same endpoint); the methods and mapping patterns are
     * what must match.
     */
    public boolean sameTarget(LoadTestSpec other) {
        return spec.targetKey().equals(other.targetKey());
    }

    /**
     * Whether this run pushed the same amount of load as {@code other}.
     *
     * <p>Latency is a function of load: 40 users against an endpoint will
     * always look worse than 5. Comparing across different concurrency reads
     * like a regression when it is only a heavier test, so an automatic
     * comparison requires the load to match. Duration may differ — throughput
     * and percentiles are rates, not totals.
     */
    public boolean sameLoad(LoadTestSpec other) {
        return spec.concurrency() == other.concurrency();
    }
}
