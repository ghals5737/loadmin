package io.github.ghals5737.loadmin.autoconfigure;

import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.RunListener;
import io.github.ghals5737.loadmin.core.query.SlowQueryRecorder;

/**
 * Points the query recorder at the run that is currently going, and unhooks it
 * afterwards so the application's queries stop being timed the moment the load
 * stops.
 */
class SlowQueryRunListener implements RunListener {

    private final SlowQueryRecorder recorder;

    SlowQueryRunListener(SlowQueryRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void started(LoadTestRun run) {
        recorder.collectInto(run::recordSlowQuery);
    }

    @Override
    public void finished(LoadTestRun run) {
        recorder.stopCollecting();
    }
}
