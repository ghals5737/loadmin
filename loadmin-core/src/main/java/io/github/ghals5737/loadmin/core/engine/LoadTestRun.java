package io.github.ghals5737.loadmin.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

import io.github.ghals5737.loadmin.core.metrics.ServerMetricsSample;

/**
 * Mutable state of one load test run. Written concurrently by the virtual-user
 * threads and the metrics sampler; {@link #view()} produces a consistent-enough
 * snapshot for the UI at any time.
 */
public class LoadTestRun {

    private final String id;
    private final LoadTestSpec spec;
    private final long startedAtMillis = System.currentTimeMillis();
    private final long startNanos = System.nanoTime();

    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile String error;
    private volatile boolean stopRequested;
    private volatile long endedNanos = -1;

    private final ConcurrentMap<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final List<ServerMetricsSample> serverMetrics = new CopyOnWriteArrayList<>();

    private static final class Bucket {
        final LongAdder count = new LongAdder();
        final LongAdder errors = new LongAdder();
        final Queue<Long> latencies = new ConcurrentLinkedQueue<>();
    }

    public LoadTestRun(String id, LoadTestSpec spec) {
        this.id = id;
        this.spec = spec;
    }

    public String id() {
        return id;
    }

    public LoadTestSpec spec() {
        return spec;
    }

    public RunStatus status() {
        return status;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long elapsedSeconds() {
        return activeNanos() / 1_000_000_000L;
    }

    public void requestStop() {
        stopRequested = true;
    }

    public boolean stopRequested() {
        return stopRequested;
    }

    void record(long latencyMillis, boolean isError) {
        long second = (System.nanoTime() - startNanos) / 1_000_000_000L;
        Bucket bucket = buckets.computeIfAbsent(second, s -> new Bucket());
        bucket.count.increment();
        if (isError) {
            bucket.errors.increment();
        } else {
            bucket.latencies.add(latencyMillis);
        }
    }

    void addServerSample(ServerMetricsSample sample) {
        serverMetrics.add(sample);
    }

    void complete(RunStatus finalStatus) {
        endedNanos = System.nanoTime();
        status = finalStatus;
    }

    void fail(String message) {
        endedNanos = System.nanoTime();
        error = message;
        status = RunStatus.FAILED;
    }

    private long activeNanos() {
        long end = endedNanos > 0 ? endedNanos : System.nanoTime();
        return end - startNanos;
    }

    public RunView view() {
        Map<Long, Bucket> sorted = new TreeMap<>(buckets);
        List<RunView.TimelinePoint> timeline = new ArrayList<>(sorted.size());
        List<Long> allLatencies = new ArrayList<>();
        long totalCount = 0;
        long totalErrors = 0;

        for (Map.Entry<Long, Bucket> entry : sorted.entrySet()) {
            Bucket bucket = entry.getValue();
            long[] latencies = bucket.latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            for (long latency : latencies) {
                allLatencies.add(latency);
            }
            long count = bucket.count.sum();
            long errors = bucket.errors.sum();
            totalCount += count;
            totalErrors += errors;
            timeline.add(new RunView.TimelinePoint(
                    entry.getKey(), count, errors,
                    LatencyStats.percentile(latencies, 50),
                    LatencyStats.percentile(latencies, 95)));
        }

        // The bucket in progress covers a fraction of a second, so plotting it
        // next to full seconds makes throughput look like it collapsed. The
        // summary still counts it — only the per-second view drops it.
        if (timeline.size() > 1) {
            timeline.remove(timeline.size() - 1);
        }

        long[] all = allLatencies.stream().mapToLong(Long::longValue).sorted().toArray();
        double activeSeconds = Math.max(activeNanos() / 1_000_000_000.0, 0.001);
        RunView.Summary summary = new RunView.Summary(
                totalCount,
                totalErrors,
                totalCount == 0 ? 0.0 : (double) totalErrors / totalCount,
                totalCount / activeSeconds,
                LatencyStats.percentile(all, 50),
                LatencyStats.percentile(all, 95),
                LatencyStats.percentile(all, 99),
                all.length == 0 ? 0 : all[all.length - 1]);

        return new RunView(id, status, error, spec, startedAtMillis, elapsedSeconds(),
                summary, timeline, List.copyOf(serverMetrics));
    }
}
