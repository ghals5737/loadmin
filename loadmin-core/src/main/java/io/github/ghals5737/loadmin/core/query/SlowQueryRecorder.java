package io.github.ghals5737.loadmin.core.query;

import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects slow statements while a load test is running, and nothing at all
 * otherwise.
 *
 * <p>Queries run on the application's own request threads, not on the load
 * generator's, so there is no way to reach the current run from where they are
 * timed. This holds that reference: the engine sets it when a run starts and
 * drops it when the run ends, and the JDBC wrapper asks here.
 */
public class SlowQueryRecorder {

    private final long thresholdMillis;
    private final AtomicReference<BiConsumer<String, Long>> sink = new AtomicReference<>();

    public SlowQueryRecorder(long thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }

    /** Where slow statements go while a run is active. */
    public void collectInto(BiConsumer<String, Long> target) {
        sink.set(target);
    }

    public void stopCollecting() {
        sink.set(null);
    }

    /** Whether timing statements is worth the trouble right now. */
    public boolean collecting() {
        return sink.get() != null;
    }

    public void record(String sql, long millis) {
        if (millis < thresholdMillis || sql == null) {
            return;
        }
        BiConsumer<String, Long> target = sink.get();
        if (target != null) {
            target.accept(sql, millis);
        }
    }

    public long thresholdMillis() {
        return thresholdMillis;
    }
}
