package io.github.ghals5737.loadmin.autoconfigure;

import java.util.List;
import java.util.Map;

import io.github.ghals5737.loadmin.core.LoadTestEndpointScanner;
import io.github.ghals5737.loadmin.core.template.ValueTemplate;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * The one place that decides whether a request may be aimed at an endpoint.
 *
 * <p>Starting a run and exporting a script both go through here: an exporter
 * that skipped this check would be a way to point loadmin's own tooling at
 * endpoints that never opted in.
 */
class TargetValidator {

    private static final PathPatternParser PATTERN_PARSER = new PathPatternParser();
    private static final int VALIDATION_SAMPLES = 20;

    private final LoadTestEndpointScanner scanner;

    TargetValidator(LoadTestEndpointScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Checks the target is a scanned {@code @LoadTest} endpoint and that the
     * path template only renders paths belonging to it.
     *
     * @return the compiled path template
     * @throws IllegalArgumentException if the endpoint is unknown, the template
     *                                  is malformed, or a rendered sample leaves
     *                                  the mapping pattern
     */
    ValueTemplate check(String httpMethod, String pathPattern, String path) {
        return check(httpMethod, pathPattern, path, Map.of());
    }

    ValueTemplate check(String httpMethod, String pathPattern, String path,
            Map<String, List<String>> valueLists) {
        boolean known = scanner.scan().stream().anyMatch(endpoint ->
                endpoint.path().equals(pathPattern)
                        && (endpoint.httpMethod().equals("ANY") || endpoint.httpMethod().equals(httpMethod)));
        if (!known) {
            throw new IllegalArgumentException("not a @LoadTest endpoint: " + httpMethod + " " + pathPattern);
        }
        ValueTemplate template = ValueTemplate.compile(path, ValueTemplate.Mode.PATH, valueLists);
        PathPattern pattern = PATTERN_PARSER.parse(pathPattern);
        int samples = template.dynamic() ? VALIDATION_SAMPLES : 1;
        for (int i = 0; i < samples; i++) {
            String rendered = template.render();
            if (!pattern.matches(PathContainer.parsePath(pathOf(rendered)))) {
                throw new IllegalArgumentException(
                        "path does not match pattern " + pathPattern + ": " + rendered);
            }
        }
        return template;
    }

    /** The path part of a rendered request, without any query string. */
    private static String pathOf(String rendered) {
        int query = rendered.indexOf('?');
        return query < 0 ? rendered : rendered.substring(0, query);
    }
}
