package io.github.ghals5737.loadmin.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ghals5737.loadmin.core.engine.LoadTestEngine;
import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.LoadTestRunRegistry;
import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;
import io.github.ghals5737.loadmin.core.engine.RunView;
import io.github.ghals5737.loadmin.core.template.ValueTemplate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend API for starting, watching and stopping load test runs, and for
 * previewing what a request template renders to.
 *
 * <p>Only endpoints discovered from {@code @LoadTest} can be targeted; see
 * {@link TargetValidator}.
 */
@RestController
public class LoadminRunController {

    private static final int PREVIEW_SAMPLES = 3;

    private final LoadTestEngine engine;
    private final LoadTestRunRegistry runs;
    private final TargetValidator targets;

    public LoadminRunController(LoadTestEngine engine, LoadTestRunRegistry runs,
            TargetValidator targets) {
        this.engine = engine;
        this.runs = runs;
        this.targets = targets;
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
        try {
            targets.check(method, request.pathPattern(), request.path());
            LoadTestSpec spec = new LoadTestSpec(
                    method,
                    request.pathPattern(),
                    request.path(),
                    request.body(),
                    request.concurrency() == null ? 10 : request.concurrency(),
                    request.durationSeconds() == null ? 15 : request.durationSeconds());
            LoadTestRun run = engine.start(spec);
            return ResponseEntity.ok(Map.of("id", run.id()));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    public record PreviewRequest(String httpMethod, String pathPattern, String path, String body) {
    }

    /**
     * Renders a few sample requests for the UI. Generators live here, on the
     * server, so the preview is produced by exactly the code the run will use.
     */
    @PostMapping("/loadmin/api/templates/preview")
    public ResponseEntity<?> preview(@RequestBody PreviewRequest request) {
        if (request.httpMethod() == null || request.pathPattern() == null || request.path() == null) {
            return badRequest("httpMethod, pathPattern and path are required");
        }
        try {
            ValueTemplate path = targets.check(
                    request.httpMethod().toUpperCase(), request.pathPattern(), request.path());
            ValueTemplate body = request.body() == null || request.body().isBlank()
                    ? null
                    : ValueTemplate.compile(request.body(), ValueTemplate.Mode.BODY);
            return ResponseEntity.ok(Map.of(
                    "paths", renderSamples(path),
                    "bodies", body == null ? List.of() : renderSamples(body)));
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
                        "target", run.spec().httpMethod() + " " + run.spec().pathTemplate(),
                        "startedAtMillis", run.startedAtMillis()))
                .toList();
    }

    private static List<String> renderSamples(ValueTemplate template) {
        List<String> samples = new ArrayList<>(PREVIEW_SAMPLES);
        for (int i = 0; i < (template.dynamic() ? PREVIEW_SAMPLES : 1); i++) {
            samples.add(template.render());
        }
        return samples;
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
