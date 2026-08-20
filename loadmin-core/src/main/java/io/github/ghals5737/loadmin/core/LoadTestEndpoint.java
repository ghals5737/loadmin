package io.github.ghals5737.loadmin.core;

/**
 * A single load-testable endpoint discovered from a {@link LoadTest} annotated
 * handler method.
 *
 * @param httpMethod   HTTP method ({@code GET}, {@code POST}, ... or {@code ANY}
 *                     when the mapping does not restrict methods)
 * @param path         URL path pattern of the mapping
 * @param handler      {@code ControllerSimpleName#methodName} for display purposes
 * @param pathTemplate request path the UI pre-fills: {@link LoadTest#path()} when
 *                     set, otherwise the mapping pattern itself
 * @param bodyTemplate request body the UI pre-fills ({@link LoadTest#body()}),
 *                     empty when the annotation does not provide one
 */
public record LoadTestEndpoint(String httpMethod, String path, String handler,
        String pathTemplate, String bodyTemplate) {
}
