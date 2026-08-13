# loadmin

> ⚠️ **Work in progress** — MVP stage (Phase 1): load engine, live results and
> server metric overlays work; parameter templates, run history and Maven Central
> publishing are still to come.

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

    @LoadTest
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
| `loadmin-core` | `@LoadTest` annotation, endpoint scanner, load engine |
| `loadmin-ui` | Static web resources for the `/loadmin` UI |
| `loadmin-spring-boot-starter` | Auto-configuration |
| `loadmin-demo` | Sample app for local verification (not published) |

## License

[Apache License 2.0](LICENSE)
