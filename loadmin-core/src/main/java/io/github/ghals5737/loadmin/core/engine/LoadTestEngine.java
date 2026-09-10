package io.github.ghals5737.loadmin.core.engine;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.ghals5737.loadmin.core.metrics.ServerMetricsSampler;
import io.github.ghals5737.loadmin.core.template.ValueTemplate;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

/**
 * Runs load tests against the application's own HTTP endpoints.
 *
 * <p>Isolation: every run gets its own worker thread pool (one thread per
 * virtual user, closed-loop: send, wait, repeat) and a dedicated Reactor Netty
 * client with its own connection pool and event loops, so nothing is shared
 * with a WebClient the application itself may use. The load still originates
 * from the same JVM — numbers are for local/dev exploration, not rigorous
 * benchmarking.
 *
 * <p>The path and body of a spec are templates: parsed once when the run starts
 * and re-rendered for every request, so virtual users spread over many keys
 * instead of hammering a single cached row.
 */
public class LoadTestEngine {

    private static final System.Logger LOG = System.getLogger(LoadTestEngine.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long GRACE_SECONDS = 30;

    private final LoadTestRunRegistry registry;
    private final Supplier<String> baseUrl;
    private final MeterRegistry meterRegistry;
    private final int maxConcurrency;
    private final int maxDurationSeconds;
    private final List<RunListener> listeners;

    public LoadTestEngine(LoadTestRunRegistry registry, Supplier<String> baseUrl,
            MeterRegistry meterRegistry, int maxConcurrency, int maxDurationSeconds) {
        this(registry, baseUrl, meterRegistry, maxConcurrency, maxDurationSeconds, List.of());
    }

    /**
     * @param onFinished called once per run after it stops, whatever its outcome
     * @deprecated use the {@link RunListener} constructor
     */
    @Deprecated
    public LoadTestEngine(LoadTestRunRegistry registry, Supplier<String> baseUrl,
            MeterRegistry meterRegistry, int maxConcurrency, int maxDurationSeconds,
            Consumer<LoadTestRun> onFinished) {
        this(registry, baseUrl, meterRegistry, maxConcurrency, maxDurationSeconds,
                onFinished == null ? List.of() : List.of(new RunListener() {
                    @Override
                    public void finished(LoadTestRun run) {
                        onFinished.accept(run);
                    }
                }));
    }

    /**
     * @param listeners notified as each run starts and stops; a listener that
     *                  throws is logged and ignored, never allowed to change the
     *                  outcome of a run
     */
    public LoadTestEngine(LoadTestRunRegistry registry, Supplier<String> baseUrl,
            MeterRegistry meterRegistry, int maxConcurrency, int maxDurationSeconds,
            List<RunListener> listeners) {
        this.registry = registry;
        this.baseUrl = baseUrl;
        this.meterRegistry = meterRegistry;
        this.maxConcurrency = maxConcurrency;
        this.maxDurationSeconds = maxDurationSeconds;
        this.listeners = List.copyOf(listeners);
    }

    public LoadTestRun start(LoadTestSpec spec) {
        validate(spec);
        List<CompiledStep> request = compile(spec);
        LoadTestRun run = new LoadTestRun(UUID.randomUUID().toString().substring(0, 8), spec);
        registry.add(run);
        Thread controller = new Thread(() -> execute(run, request), "loadmin-run-" + run.id());
        controller.setDaemon(true);
        controller.start();
        return run;
    }

    private void validate(LoadTestSpec spec) {
        if (spec.concurrency() < 1 || spec.concurrency() > maxConcurrency) {
            throw new IllegalArgumentException(
                    "concurrency must be between 1 and " + maxConcurrency);
        }
        if (spec.durationSeconds() < 1 || spec.durationSeconds() > maxDurationSeconds) {
            throw new IllegalArgumentException(
                    "durationSeconds must be between 1 and " + maxDurationSeconds);
        }
        if (spec.steps().isEmpty()) {
            throw new IllegalArgumentException("a run needs at least one step");
        }
        spec.steps().forEach(step -> HttpMethod.valueOf(step.httpMethod()));
    }

    /**
     * Parses the request templates up front, so a malformed one fails the start
     * call (surfacing as a 400) instead of the run itself.
     */
    private List<CompiledStep> compile(LoadTestSpec spec) {
        List<CompiledStep> compiled = new ArrayList<>(spec.steps().size());
        for (LoadTestSpec.Step step : spec.steps()) {
            ValueTemplate path = ValueTemplate.compile(step.pathTemplate(), ValueTemplate.Mode.PATH);
            ValueTemplate body = step.bodyTemplate() == null || step.bodyTemplate().isBlank()
                    ? null
                    : ValueTemplate.compile(step.bodyTemplate(), ValueTemplate.Mode.BODY);
            compiled.add(new CompiledStep(HttpMethod.valueOf(step.httpMethod()), path, body));
        }
        return List.copyOf(compiled);
    }

    /** A step's templates, parsed once and shared by every virtual user. */
    private record CompiledStep(HttpMethod method, ValueTemplate path, ValueTemplate body) {
    }

    private void execute(LoadTestRun run, List<CompiledStep> steps) {
        LoadTestSpec spec = run.spec();
        notifyListeners(listener -> listener.started(run), run);
        String name = "loadmin-" + run.id();
        ConnectionProvider connections = ConnectionProvider.create(name, spec.concurrency());
        LoopResources loops = LoopResources.create(name + "-io", 1,
                Math.max(2, Runtime.getRuntime().availableProcessors() / 4), true);
        ExecutorService workers = Executors.newFixedThreadPool(
                spec.concurrency(), namedDaemonFactory(name + "-vu-"));
        ScheduledExecutorService samplerExecutor = Executors.newSingleThreadScheduledExecutor(
                namedDaemonFactory(name + "-sampler-"));
        try {
            HttpClient httpClient = HttpClient.create(connections)
                    .runOn(loops)
                    .responseTimeout(REQUEST_TIMEOUT);
            WebClient client = WebClient.builder()
                    .baseUrl(baseUrl.get())
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            if (meterRegistry != null) {
                ServerMetricsSampler sampler = new ServerMetricsSampler(meterRegistry);
                samplerExecutor.scheduleAtFixedRate(
                        () -> run.addServerSample(sampler.sample(run.elapsedSeconds())),
                        0, 1, TimeUnit.SECONDS);
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(spec.durationSeconds());
            CountDownLatch done = new CountDownLatch(spec.concurrency());
            for (int i = 0; i < spec.concurrency(); i++) {
                workers.execute(() -> {
                    try {
                        runVirtualUser(run, client, deadline, steps);
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await(spec.durationSeconds() + GRACE_SECONDS, TimeUnit.SECONDS);
            run.complete(run.stopRequested() ? RunStatus.STOPPED : RunStatus.COMPLETED);
        } catch (Exception e) {
            run.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            samplerExecutor.shutdownNow();
            workers.shutdownNow();
            connections.dispose();
            loops.dispose();
            notifyListeners(listener -> listener.finished(run), run);
        }
    }

    /** A listener must never turn a finished run into a failed one. */
    private void notifyListeners(Consumer<RunListener> notification, LoadTestRun run) {
        for (RunListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "loadmin: run listener failed for " + run.id(), e);
            }
        }
    }

    /**
     * One virtual user: walk the scenario from the first step to the last, then
     * start over, until the run is done. Steps are timed separately so a slow
     * one cannot hide behind the others, and a failing step does not skip the
     * rest of the scenario — a login that 500s should still show what the
     * following calls do.
     */
    private void runVirtualUser(LoadTestRun run, WebClient client, long deadline,
            List<CompiledStep> steps) {
        while (System.nanoTime() < deadline && !run.stopRequested()) {
            for (int index = 0; index < steps.size(); index++) {
                if (System.nanoTime() >= deadline || run.stopRequested()) {
                    return;
                }
                CompiledStep step = steps.get(index);
                long started = System.nanoTime();
                boolean error;
                try {
                    WebClient.RequestBodySpec bodySpec = client.method(step.method())
                            .uri(step.path().render());
                    WebClient.RequestHeadersSpec<?> request = step.body() == null
                            ? bodySpec
                            : bodySpec.contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(step.body().render());
                    HttpStatusCode status = request
                            .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode()))
                            .block(REQUEST_TIMEOUT.plusSeconds(1));
                    error = status == null || status.isError();
                } catch (Exception e) {
                    error = true;
                }
                run.record(index, (System.nanoTime() - started) / 1_000_000, error);
            }
        }
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
