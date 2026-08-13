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
}
