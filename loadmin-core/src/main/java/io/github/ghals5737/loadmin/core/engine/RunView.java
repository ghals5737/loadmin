package io.github.ghals5737.loadmin.core.engine;

import java.util.List;

import io.github.ghals5737.loadmin.core.metrics.ServerMetricsSample;

/**
 * Snapshot of a run for the UI: overall summary, per-second timeline and
 * server-side metric samples. Safe to compute while the run is still going.
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
        List<ServerMetricsSample> serverMetrics) {

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
     * One second of the run. Percentiles cover successful requests recorded in
     * that second.
     */
    public record TimelinePoint(long t, long count, long errors, long p50, long p95) {
    }
}
