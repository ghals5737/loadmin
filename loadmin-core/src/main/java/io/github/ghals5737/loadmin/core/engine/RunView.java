package io.github.ghals5737.loadmin.core.engine;

import java.util.List;

import io.github.ghals5737.loadmin.core.metrics.ServerMetricsSample;
import io.github.ghals5737.loadmin.core.query.SlowQuery;

/**
 * Snapshot of a run for the UI: overall summary, per-second timeline, server
 * metric samples and any slow SQL caught while the load was on. Safe to compute
 * while the run is still going.
 */
public record RunView(
        String id,
        RunStatus status,
        String error,
        LoadTestSpec spec,
        long startedAtMillis,
        long elapsedSeconds,
        Summary summary,
        List<TimelinePoint> timeline,
        List<ServerMetricsSample> serverMetrics,
        List<SlowQuery> slowQueries,
        List<StepSummary> steps) {

    public record Summary(
            long requests,
            long errors,
            double errorRate,
            double rps,
            long p50,
            long p95,
            long p99,
            long max) {
    }

    /**
     * How one step of a scenario did over the whole run. A single-endpoint run
     * has one of these, matching the overall summary.
     *
     * @param index position in the scenario, from 0
     * @param name  the step's label
     */
    public record StepSummary(int index, String name, Summary summary) {
    }

    /**
     * One full second of the run. Percentiles cover successful requests
     * recorded in that second; the trailing partial second is not included.
     */
    public record TimelinePoint(long t, long count, long errors, long p50, long p95) {
    }
}
