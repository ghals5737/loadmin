package io.github.ghals5737.loadmin.core.engine;

/**
 * Notified as a run starts and stops.
 *
 * <p>Used to record history and to switch on collectors that live outside the
 * engine — slow query capture only makes sense while a run is on.
 */
public interface RunListener {

    default void started(LoadTestRun run) {
    }

    default void finished(LoadTestRun run) {
    }
}
