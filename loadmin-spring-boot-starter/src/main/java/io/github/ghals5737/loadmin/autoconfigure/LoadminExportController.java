package io.github.ghals5737.loadmin.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;
import io.github.ghals5737.loadmin.core.export.ExportRequest;
import io.github.ghals5737.loadmin.core.export.ScriptExporter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exports a configured load test as a script for an external runner.
 *
 * <p>The built-in engine shares a JVM with the application it measures. This is
 * the way out of that: the same target, templates and load settings, handed to
 * a runner that lives in another process.
 */
@RestController
public class LoadminExportController {

    private final Map<String, ScriptExporter> exporters = new LinkedHashMap<>();
    private final TargetValidator targets;
    private final Supplier<String> baseUrl;

    public LoadminExportController(List<ScriptExporter> exporters, TargetValidator targets,
            Supplier<String> baseUrl) {
        exporters.forEach(exporter -> this.exporters.put(exporter.id(), exporter));
        this.targets = targets;
        this.baseUrl = baseUrl;
    }

    /** Same shape as starting a run: a scenario, or one target given flat. */
    public record ExportScriptRequest(List<LoadminRunController.StepRequest> steps,
            String httpMethod, String pathPattern, String path, String body,
            Integer concurrency, Integer durationSeconds) {
    }

    /** The formats this build can produce, for the UI to offer. */
    @GetMapping("/loadmin/api/export")
    public List<String> formats() {
        return List.copyOf(exporters.keySet());
    }

    @PostMapping("/loadmin/api/export/{format}")
    public ResponseEntity<?> export(@PathVariable String format,
            @RequestBody ExportScriptRequest request) {
        ScriptExporter exporter = exporters.get(format);
        if (exporter == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "unknown export format: " + format + "; supported: " + exporters.keySet()));
        }
        List<LoadminRunController.StepRequest> steps = LoadminRunController.stepsOf(
                new LoadminRunController.StartRunRequest(request.steps(), request.httpMethod(),
                        request.pathPattern(), request.path(), request.body(),
                        request.concurrency(), request.durationSeconds()));
        if (steps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "httpMethod, pathPattern and path are required"));
        }
        try {
            List<LoadTestSpec.Step> checked = new ArrayList<>(steps.size());
            for (LoadminRunController.StepRequest step : steps) {
                if (step.httpMethod() == null || step.pathPattern() == null || step.path() == null) {
                    throw new IllegalArgumentException(
                            "every step needs httpMethod, pathPattern and path");
                }
                String method = step.httpMethod().toUpperCase();
                // Same guard as starting a run: export is not a way around it.
                targets.check(method, step.pathPattern(), step.path());
                checked.add(new LoadTestSpec.Step(step.name(), method, step.pathPattern(),
                        step.path(), step.body()));
            }
            ExportRequest exportRequest = new ExportRequest(baseUrl.get(), new LoadTestSpec(checked,
                    request.concurrency() == null ? 10 : request.concurrency(),
                    request.durationSeconds() == null ? 15 : request.durationSeconds()));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + exporter.fileName(exportRequest) + "\"")
                    .body(exporter.render(exportRequest));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
