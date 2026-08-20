package io.github.ghals5737.loadmin.core.engine;

/**
 * Configuration of a single load test run.
 *
 * @param httpMethod      HTTP method to send ({@code GET}, {@code POST}, ...)
 * @param pathPattern     the mapping pattern of the target endpoint, used to
 *                        identify it against the scanned {@code @LoadTest} list
 * @param pathTemplate    the request path, path variables filled in and query
 *                        string included; may contain {@code ${...}}
 *                        placeholders that are rendered per request
 * @param bodyTemplate    optional JSON request body, also a template;
 *                        {@code null} or blank for none
 * @param concurrency     number of concurrent virtual users
 * @param durationSeconds how long to keep the load running
 */
public record LoadTestSpec(
        String httpMethod,
        String pathPattern,
        String pathTemplate,
        String bodyTemplate,
        int concurrency,
        int durationSeconds) {
}
