package io.github.ghals5737.loadmin.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ghals5737.loadmin.core.engine.LoadTestEngine;
import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.LoadTestRunRegistry;
import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;
import io.github.ghals5737.loadmin.core.engine.RunOptions;
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
 * <p>Only endpoints discovered from {@code @LoadTest} can be targeted, and
 * every step of a scenario is checked; see {@link TargetValidator}.
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

    /** One request of a scenario, as the UI sends it. */
    public record StepRequest(String name, String httpMethod, String pathPattern,
            String path, String body) {
    }

    /**
     * Either a scenario ({@code steps}) or a single target given by the flat
     * fields, which is the same thing with one step.
     */
    public record StartRunRequest(List<StepRequest> steps, String httpMethod, String pathPattern,
            String path, String body, Integer concurrency, Integer durationSeconds,
            Map<String, String> headers, Map<String, List<String>> valueLists) {
    }

    @PostMapping("/loadmin/api/runs")
    public ResponseEntity<?> start(@RequestBody StartRunRequest request) {
        List<StepRequest> steps = stepsOf(request);
        if (steps.isEmpty()) {
            return badRequest("a run needs at least one step");
        }
        try {
            RunOptions options = new RunOptions(request.headers(), request.valueLists());
            List<LoadTestSpec.Step> checked = new ArrayList<>(steps.size());
            for (StepRequest step : steps) {
                checked.add(check(step, options.valueLists()));
            }
            LoadTestSpec spec = new LoadTestSpec(checked,
                    request.concurrency() == null ? 10 : request.concurrency(),
                    request.durationSeconds() == null ? 15 : request.durationSeconds());
            LoadTestRun run = engine.start(spec, options);
            return ResponseEntity.ok(Map.of("id", run.id()));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    public record PreviewRequest(String httpMethod, String pathPattern, String path, String body,
            Map<String, List<String>> valueLists) {
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
            Map<String, List<String>> lists = new RunOptions(Map.of(), request.valueLists())
                    .valueLists();
            targets.check(request.httpMethod().toUpperCase(), request.pathPattern(),
                    request.path(), lists);
            // A fresh template for the samples: the validator rendered from its
            // own copy, which would leave ${cycle} and ${seq} mid-count here even
            // though the run itself starts from the beginning.
            ValueTemplate path = ValueTemplate.compile(
                    request.path(), ValueTemplate.Mode.PATH, lists);
            ValueTemplate body = request.body() == null || request.body().isBlank()
                    ? null
                    : ValueTemplate.compile(request.body(), ValueTemplate.Mode.BODY, lists);
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
                        "target", run.spec().label(),
                        "startedAtMillis", run.startedAtMillis()))
                .toList();
    }

    /** Accepts a scenario, or the flat single-target form as one step. */
    static List<StepRequest> stepsOf(StartRunRequest request) {
        if (request.steps() != null && !request.steps().isEmpty()) {
            return request.steps();
        }
        if (request.httpMethod() == null || request.pathPattern() == null || request.path() == null) {
            return List.of();
        }
        return List.of(new StepRequest(null, request.httpMethod(), request.pathPattern(),
                request.path(), request.body()));
    }

    private LoadTestSpec.Step check(StepRequest step, Map<String, List<String>> valueLists) {
        if (step.httpMethod() == null || step.pathPattern() == null || step.path() == null) {
            throw new IllegalArgumentException("every step needs httpMethod, pathPattern and path");
        }
        String method = step.httpMethod().toUpperCase();
        targets.check(method, step.pathPattern(), step.path(), valueLists);
        return new LoadTestSpec.Step(step.name(), method, step.pathPattern(), step.path(), step.body());
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
