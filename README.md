# loadmin

> ⚠️ **Work in progress** — the load engine, live results, server metric
> overlays, request templates and run history all work; Maven Central publishing
> and k6/Gatling export are still to come.

Annotation-driven load testing UI for Spring Boot — like Swagger UI, but for load tests.

Put `@LoadTest` on a controller handler, start your app, and open `/loadmin`:
a load testing web page is exposed automatically. Unlike external tools (k6, Gatling,
JMeter), loadmin lives **inside** your application, so it can overlay server-side
metrics (Tomcat thread pool, HikariCP connection pool, GC pauses) on top of the
latency graph while the load is running.

## Quick start

```java
@RestController
public class UserController {

    @LoadTest(path = "/api/users/${int(1,20)}")
    @GetMapping("/api/users/{id}")
    public User user(@PathVariable long id) { ... }
}
```

```yaml
# application.yml — loadmin is disabled by default; explicit opt-in required
loadmin:
  enabled: true
```

Then open `http://localhost:8080/loadmin`, pick an endpoint, set concurrent
users and duration, and start. You get live p50/p95/p99 latency, throughput and
error rate — and, aligned on the same time axis, what the server was doing
while it took the load:

![loadmin run view](docs/screenshot-run.png)
![server metric overlay](docs/screenshot-metrics.png)

### Request templates

A load test that calls the same URL for every request measures your caches, not
your endpoint. Paths, query strings and JSON bodies are templates, re-rendered
per request:

| Placeholder | Renders |
|-------------|---------|
| `${int(1,20)}` | a random integer, both bounds included |
| `${seq}`, `${seq(1000)}` | an increasing counter, shared by all virtual users |
| `${uuid}` | a random UUID |
| `${alpha(8)}` | a random `[a-z0-9]` string |
| `${pick(a\|b\|c)}` | one of the given values |
| `${now}` | epoch milliseconds |

```java
@LoadTest(
        path = "/api/orders?channel=${pick(web|app)}",
        body = "{\"item\": \"${pick(shoes|bags)}\", \"qty\": ${int(1,5)}}")
@PostMapping("/api/orders")
public Order create(@RequestBody OrderRequest request) { ... }
```

The annotation only supplies the defaults — the UI shows rendered samples as you
type and everything stays editable there. Only endpoints carrying `@LoadTest`
can be targeted, and generated values can never contain path or query characters,
so a template cannot be used to reach a different endpoint.

### Run history

Finished runs are stored as JSON under `.loadmin/history`, so a run can be
compared with the previous one on the same endpoint — the run view shows the
delta in p50/p95/req-s/error rate and overlays the previous p95 on the latency
chart, and any two runs of an endpoint can be compared side by side.

### Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `loadmin.enabled` | `false` | Required opt-in; nothing is registered without it |
| `loadmin.max-concurrency` | `200` | Upper bound for concurrent users in a run |
| `loadmin.max-duration-seconds` | `300` | Upper bound for a run's duration |
| `loadmin.history.enabled` | `true` | Write finished runs to disk |
| `loadmin.history.dir` | `.loadmin/history` | Where run files are written |
| `loadmin.history.max-runs` | `100` | Runs to keep; the oldest are deleted |

### Server metric overlay

The overlay reads from the app's Micrometer `MeterRegistry`:

| Chart | Meters | Needs |
|-------|--------|-------|
| Tomcat threads busy | `tomcat.threads.*` | actuator + `server.tomcat.mbeanregistry.enabled=true` |
| HikariCP connections | `hikaricp.connections.*` | actuator + a pooled DataSource |
| GC pause | `jvm.gc.pause` | actuator |

Without a `MeterRegistry` bean the load test still works; you just get the
client-side charts only.

## ⚠️ Do not enable in production

loadmin generates load against your own application. Enabling it in production is
a self-DDoS button. It stays completely inactive (no beans registered) unless
`loadmin.enabled=true` is set explicitly.

Note that the built-in load generator runs in the same JVM as your application.
Numbers are useful for local/dev exploration, not for rigorous benchmarking —
an external-runner mode (k6/Gatling script export) is on the roadmap.

## Modules

| Module | Description |
|--------|-------------|
| `loadmin-core` | `@LoadTest` annotation, endpoint scanner, load engine, templates, history |
| `loadmin-ui` | Static web resources for the `/loadmin` UI |
| `loadmin-spring-boot-starter` | Auto-configuration |
| `loadmin-demo` | Sample app for local verification (not published) |

## License

[Apache License 2.0](LICENSE)
