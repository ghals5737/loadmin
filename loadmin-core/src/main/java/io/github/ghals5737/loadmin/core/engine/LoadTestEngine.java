package io.github.ghals5737.loadmin.core.engine;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long GRACE_SECONDS = 30;

    private final LoadTestRunRegistry registry;
    private final Supplier<String> baseUrl;
    private final MeterRegistry meterRegistry;
    private final int maxConcurrency;
    private final int maxDurationSeconds;

    public LoadTestEngine(LoadTestRunRegistry registry, Supplier<String> baseUrl,
            MeterRegistry meterRegistry, int maxConcurrency, int maxDurationSeconds) {
        this.registry = registry;
        this.baseUrl = baseUrl;
        this.meterRegistry = meterRegistry;
        this.maxConcurrency = maxConcurrency;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public LoadTestRun start(LoadTestSpec spec) {
        validate(spec);
        CompiledRequest request = compile(spec);
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
        HttpMethod.valueOf(spec.httpMethod());
    }

    /**
     * Parses the request templates up front, so a malformed one fails the start
     * call (surfacing as a 400) instead of the run itself.
     */
    private CompiledRequest compile(LoadTestSpec spec) {
        ValueTemplate path = ValueTemplate.compile(spec.pathTemplate(), ValueTemplate.Mode.PATH);
        ValueTemplate body = spec.bodyTemplate() == null || spec.bodyTemplate().isBlank()
                ? null
                : ValueTemplate.compile(spec.bodyTemplate(), ValueTemplate.Mode.BODY);
        return new CompiledRequest(path, body);
    }

    /** A spec's templates, parsed once and shared by every virtual user. */
    private record CompiledRequest(ValueTemplate path, ValueTemplate body) {
    }

    private void execute(LoadTestRun run, CompiledRequest request) {
        LoadTestSpec spec = run.spec();
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
                        runVirtualUser(run, client, deadline, request);
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
        }
    }

    private void runVirtualUser(LoadTestRun run, WebClient client, long deadline,
            CompiledRequest compiled) {
        HttpMethod method = HttpMethod.valueOf(run.spec().httpMethod());

        while (System.nanoTime() < deadline && !run.stopRequested()) {
            long started = System.nanoTime();
            boolean error;
            try {
                WebClient.RequestBodySpec bodySpec = client.method(method)
                        .uri(compiled.path().render());
                WebClient.RequestHeadersSpec<?> request = compiled.body() == null
                        ? bodySpec
                        : bodySpec.contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(compiled.body().render());
                HttpStatusCode status = request
                        .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode()))
                        .block(REQUEST_TIMEOUT.plusSeconds(1));
                error = status == null || status.isError();
            } catch (Exception e) {
                error = true;
            }
            run.record((System.nanoTime() - started) / 1_000_000, error);
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
