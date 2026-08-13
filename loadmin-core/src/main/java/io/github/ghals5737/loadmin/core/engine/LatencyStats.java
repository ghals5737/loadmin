package io.github.ghals5737.loadmin.core.engine;

final class LatencyStats {

    private LatencyStats() {
    }

    /**
     * Nearest-rank percentile over an ascending-sorted array; 0 when empty.
     */
    static long percentile(long[] sortedAsc, double pct) {
        if (sortedAsc.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(pct / 100.0 * sortedAsc.length) - 1;
        return sortedAsc[Math.max(0, Math.min(index, sortedAsc.length - 1))];
    }
}
