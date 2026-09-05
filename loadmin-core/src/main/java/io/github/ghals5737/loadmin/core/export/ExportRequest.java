package io.github.ghals5737.loadmin.core.export;

/**
 * What a generated script has to reproduce: the request, and the load to put
 * on it.
 *
 * @param baseUrl         address of the application under test, used as the
 *                        script's default target
 * @param httpMethod      HTTP method to send
 * @param pathPattern     mapping pattern of the target, used to name things
 * @param pathTemplate    request path, may contain {@code ${...}} placeholders
 * @param bodyTemplate    request body template; {@code null} or blank for none
 * @param concurrency     number of concurrent virtual users
 * @param durationSeconds how long to keep the load running
 */
public record ExportRequest(
        String baseUrl,
        String httpMethod,
        String pathPattern,
        String pathTemplate,
        String bodyTemplate,
        int concurrency,
        int durationSeconds) {
}
