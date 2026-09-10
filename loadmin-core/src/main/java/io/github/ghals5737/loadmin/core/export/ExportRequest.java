package io.github.ghals5737.loadmin.core.export;

import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;

/**
 * What a generated script has to reproduce: the scenario, and the load to put
 * on it.
 *
 * @param baseUrl address of the application under test, used as the script's
 *                default target
 * @param spec    the steps, the concurrency and the duration
 */
public record ExportRequest(String baseUrl, LoadTestSpec spec) {
}
