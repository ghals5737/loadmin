package io.github.ghals5737.loadmin.core.engine;

import java.util.List;

/**
 * Configuration of a single load test run: the steps each virtual user walks
 * through, and how hard to push them.
 *
 * <p>A test against one endpoint is a scenario with one step, so there is one
 * shape to handle everywhere downstream.
 *
 * @param steps           requests to send in order, once per iteration
 * @param concurrency     number of concurrent virtual users
 * @param durationSeconds how long to keep the load running
 */
public record LoadTestSpec(List<Step> steps, int concurrency, int durationSeconds) {

    public LoadTestSpec {
        steps = List.copyOf(steps);
    }

    /**
     * One request in a scenario.
     *
     * @param name         label for the results table; the method and pattern
     *                     when not given
     * @param httpMethod   HTTP method to send
     * @param pathPattern  mapping pattern of the target endpoint, used to check
     *                     it against the scanned {@code @LoadTest} list
     * @param pathTemplate the request path, path variables filled in and query
     *                     string included; may contain {@code ${...}}
     *                     placeholders rendered per request
     * @param bodyTemplate optional JSON body, also a template
     */
    public record Step(String name, String httpMethod, String pathPattern,
            String pathTemplate, String bodyTemplate) {

        public Step {
            if (name == null || name.isBlank()) {
                name = httpMethod + " " + pathPattern;
            }
        }

        public Step(String httpMethod, String pathPattern, String pathTemplate, String bodyTemplate) {
            this(null, httpMethod, pathPattern, pathTemplate, bodyTemplate);
        }
    }

    /** A run against a single endpoint. */
    public static LoadTestSpec single(String httpMethod, String pathPattern, String pathTemplate,
            String bodyTemplate, int concurrency, int durationSeconds) {
        return new LoadTestSpec(
                List.of(new Step(httpMethod, pathPattern, pathTemplate, bodyTemplate)),
                concurrency, durationSeconds);
    }

    public boolean singleStep() {
        return steps.size() == 1;
    }

    /** What was tested, for headings and history rows. */
    public String label() {
        if (singleStep()) {
            Step step = steps.get(0);
            return step.httpMethod() + " " + step.pathTemplate();
        }
        return steps.size() + " steps: "
                + String.join(" → ", steps.stream().map(Step::name).toList());
    }

    /**
     * Identifies the scenario for comparison: two runs are the same test when
     * they walked the same endpoints in the same order. Path templates may
     * differ — a wider {@code ${int(...)}} range is still the same endpoint.
     */
    public String targetKey() {
        return String.join(" > ", steps.stream()
                .map(step -> step.httpMethod() + " " + step.pathPattern())
                .toList());
    }
}
