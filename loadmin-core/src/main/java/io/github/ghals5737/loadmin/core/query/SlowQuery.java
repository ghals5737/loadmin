package io.github.ghals5737.loadmin.core.query;

/**
 * One SQL statement that ran slowly during a load test, aggregated over every
 * execution of it in that run.
 *
 * @param sql         the statement text as the driver received it
 * @param count       how many executions crossed the threshold
 * @param maxMillis   the slowest of them
 * @param totalMillis time spent in the slow executions, the field to sort by —
 *                    a 40ms statement run 500 times hurts more than a one-off
 *                    900ms outlier
 */
public record SlowQuery(String sql, long count, long maxMillis, long totalMillis) {

    public long averageMillis() {
        return count == 0 ? 0 : totalMillis / count;
    }
}
