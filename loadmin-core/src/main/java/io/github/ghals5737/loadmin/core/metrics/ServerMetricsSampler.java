package io.github.ghals5737.loadmin.core.metrics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Samples server-internal metrics from the Micrometer registry while a load
 * test is running: Tomcat thread pool, HikariCP connection pool and GC pauses.
 *
 * <p>One instance per run — GC pause is reported as a delta per sample tick,
 * which needs per-run state. Metrics that are not bound in the registry (e.g.
 * HikariCP without a DataSource, Tomcat metrics without actuator) are simply
 * omitted from the sample.
 */
public class ServerMetricsSampler {

    private final MeterRegistry registry;
    private Double lastGcTotalMs;

    public ServerMetricsSampler(MeterRegistry registry) {
        this.registry = registry;
    }

    public ServerMetricsSample sample(long t) {
        Map<String, Double> values = new LinkedHashMap<>();
        putGaugeSum(values, "tomcatThreadsBusy", "tomcat.threads.busy");
        putGaugeSum(values, "tomcatThreadsMax", "tomcat.threads.config.max");
        putGaugeSum(values, "hikariActive", "hikaricp.connections.active");
        putGaugeSum(values, "hikariPending", "hikaricp.connections.pending");
        putGaugeSum(values, "hikariMax", "hikaricp.connections.max");

        Double gcTotal = gcPauseTotalMs();
        if (gcTotal != null) {
            values.put("gcPauseMs", lastGcTotalMs == null ? 0.0 : Math.max(0.0, gcTotal - lastGcTotalMs));
            lastGcTotalMs = gcTotal;
        }
        return new ServerMetricsSample(t, values);
    }

    private void putGaugeSum(Map<String, Double> values, String key, String meterName) {
        Collection<Gauge> gauges = registry.find(meterName).gauges();
        if (!gauges.isEmpty()) {
            values.put(key, gauges.stream().mapToDouble(Gauge::value).sum());
        }
    }

    /**
     * Total GC pause time so far, or null when no GC has been observed yet
     * (the {@code jvm.gc.pause} timer is registered lazily on first GC).
     */
    private Double gcPauseTotalMs() {
        Collection<Timer> timers = registry.find("jvm.gc.pause").timers();
        if (timers.isEmpty()) {
            return null;
        }
        return timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS)).sum();
    }
}
