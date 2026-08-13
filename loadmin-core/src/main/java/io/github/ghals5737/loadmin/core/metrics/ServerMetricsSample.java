package io.github.ghals5737.loadmin.core.metrics;

import java.util.Map;

/**
 * Server-side metric values sampled at one point of a run.
 *
 * @param t      seconds since the run started
 * @param values metric key to value; only metrics that exist in the registry
 *               are present (e.g. no {@code hikari*} keys without a DataSource)
 */
public record ServerMetricsSample(long t, Map<String, Double> values) {
}
