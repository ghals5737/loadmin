package io.github.ghals5737.loadmin.autoconfigure;

import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.RunListener;
import io.github.ghals5737.loadmin.core.history.RunHistoryStore;

/** Writes each finished run to the history store. */
class HistoryRunListener implements RunListener {

    private final RunHistoryStore history;

    HistoryRunListener(RunHistoryStore history) {
        this.history = history;
    }

    @Override
    public void finished(LoadTestRun run) {
        history.save(run.view());
    }
}
