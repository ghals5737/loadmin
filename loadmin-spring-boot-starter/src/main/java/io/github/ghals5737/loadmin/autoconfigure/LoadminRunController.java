package io.github.ghals5737.loadmin.autoconfigure;

import java.util.List;
import java.util.Map;

import io.github.ghals5737.loadmin.core.LoadTestEndpointScanner;
import io.github.ghals5737.loadmin.core.engine.LoadTestEngine;
import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.LoadTestRunRegistry;
import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;
import io.github.ghals5737.loadmin.core.engine.RunView;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Backend API for starting, watching and stopping load test runs.
 *
 * <p>Only endpoints discovered from {@code @LoadTest} can be targeted; the
 * requested concrete path must match the endpoint's mapping pattern.
 */
@RestController
public class LoadminRunController {

    private static final PathPatternParser PATTERN_PARSER = new PathPatternParser();

    private final LoadTestEngine engine;
    private final LoadTestRunRegistry runs;
    private final LoadTestEndpointScanner scanner;

    public LoadminRunController(LoadTestEngine engine, LoadTestRunRegistry runs,
            LoadTestEndpointScanner scanner) {
        this.engine = engine;
        this.runs = runs;
        this.scanner = scanner;
    }

    public record StartRunRequest(String httpMethod, String pathPattern, String path,
            String body, Integer concurrency, Integer durationSeconds) {
    }

    @PostMapping("/loadmin/api/runs")
    public ResponseEntity<?> start(@RequestBody StartRunRequest request) {
        if (request.httpMethod() == null || request.pathPattern() == null || request.path() == null) {
            return badRequest("httpMethod, pathPattern and path are required");
        }
        String method = request.httpMethod().toUpperCase();
        boolean known = scanner.scan().stream().anyMatch(endpoint ->
                endpoint.path().equals(request.pathPattern())
                        && (endpoint.httpMethod().equals("ANY") || endpoint.httpMethod().equals(method)));
        if (!known) {
            return badRequest("not a @LoadTest endpoint: " + method + " " + request.pathPattern());
        }
        if (!PATTERN_PARSER.parse(request.pathPattern())
                .matches(PathContainer.parsePath(request.path()))) {
            return badRequest("path does not match pattern " + request.pathPattern());
        }
        LoadTestSpec spec = new LoadTestSpec(
                method,
                request.pathPattern(),
                request.path(),
                request.body(),
                request.concurrency() == null ? 10 : request.concurrency(),
                request.durationSeconds() == null ? 15 : request.durationSeconds());
        try {
            LoadTestRun run = engine.start(spec);
            return ResponseEntity.ok(Map.of("id", run.id()));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/loadmin/api/runs/{id}")
    public ResponseEntity<RunView> get(@PathVariable String id) {
        LoadTestRun run = runs.get(id);
        return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(run.view());
    }

    @PostMapping("/loadmin/api/runs/{id}/stop")
    public ResponseEntity<?> stop(@PathVariable String id) {
        LoadTestRun run = runs.get(id);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }
        run.requestStop();
        return ResponseEntity.ok(Map.of("id", id, "status", run.status().name()));
    }

    @GetMapping("/loadmin/api/runs")
    public List<Map<String, Object>> list() {
        return runs.all().stream()
                .<Map<String, Object>>map(run -> Map.of(
                        "id", run.id(),
                        "status", run.status().name(),
                        "target", run.spec().httpMethod() + " " + run.spec().path(),
                        "startedAtMillis", run.startedAtMillis()))
                .toList();
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
