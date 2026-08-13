package io.github.ghals5737.loadmin.core.engine;

/**
 * Configuration of a single load test run.
 *
 * @param httpMethod      HTTP method to send ({@code GET}, {@code POST}, ...)
 * @param pathPattern     the mapping pattern of the target endpoint, used to
 *                        identify it against the scanned {@code @LoadTest} list
 * @param path            the concrete request path (pattern variables resolved)
 * @param body            optional JSON request body; {@code null} or blank for none
 * @param concurrency     number of concurrent virtual users
 * @param durationSeconds how long to keep the load running
 */
public record LoadTestSpec(
        String httpMethod,
        String pathPattern,
        String path,
        String body,
        int concurrency,
        int durationSeconds) {
}
