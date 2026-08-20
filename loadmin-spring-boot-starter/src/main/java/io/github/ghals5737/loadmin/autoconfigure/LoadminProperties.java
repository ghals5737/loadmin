package io.github.ghals5737.loadmin.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loadmin")
public class LoadminProperties {

    /**
     * Explicit opt-in. When false (the default) no loadmin bean is registered.
     */
    private boolean enabled = false;

    /**
     * Upper bound for the concurrency a run may request.
     */
    private int maxConcurrency = 200;

    /**
     * Upper bound for the duration (seconds) a run may request.
     */
    private int maxDurationSeconds = 300;

    private final History history = new History();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public History getHistory() {
        return history;
    }

    /**
     * Where finished runs are kept so a run can be compared with the previous
     * one on the same endpoint.
     */
    public static class History {

        /**
         * Whether finished runs are written to disk. When false, results are
         * only kept in memory for the lifetime of the application.
         */
        private boolean enabled = true;

        /**
         * Directory for the run files, relative to the working directory unless
         * absolute. Created on the first saved run.
         */
        private String dir = ".loadmin/history";

        /**
         * How many runs to keep; the oldest are deleted beyond this.
         */
        private int maxRuns = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public int getMaxRuns() {
            return maxRuns;
        }

        public void setMaxRuns(int maxRuns) {
            this.maxRuns = maxRuns;
        }
    }
}
