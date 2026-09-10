package io.github.ghals5737.loadmin.demo;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.github.ghals5737.loadmin.core.LoadTest;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SampleApiController {

    private final JdbcTemplate jdbc;

    public SampleApiController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @LoadTest
    @GetMapping("/hello")
    public Map<String, String> hello() throws InterruptedException {
        simulateWork();
        return Map.of("message", "hello");
    }

    @LoadTest(path = "/api/users/${int(1,20)}")
    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable long id) {
        long dbId = Math.floorMod(id, 20) + 1;
        // Holds the connection while "processing" so HikariCP pool pressure
        // (active/pending) is visible in the loadmin metric overlay.
        return jdbc.execute((ConnectionCallback<Map<String, Object>>) connection -> {
            try (PreparedStatement statement = connection
                    .prepareStatement("select id, name from users where id = ?")) {
                statement.setLong(1, dbId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    Map<String, Object> row = Map.of(
                            "id", resultSet.getLong(1),
                            "name", resultSet.getString(2));
                    Thread.sleep(ThreadLocalRandom.current().nextLong(10, 40));
                    return row;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
    }

    @LoadTest(body = """
            {"item": "${pick(shoes|bags|hat)}", "qty": ${int(1,5)}, "orderNo": "${uuid}"}""")
    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> order)
            throws InterruptedException {
        simulateWork();
        return Map.of("status", "created", "order", order);
    }

    @LoadTest(path = "/api/search?q=${pick(shoes|bags|hat)}&page=${int(1,5)}")
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q, @RequestParam int page)
            throws InterruptedException {
        simulateWork();
        return Map.of("q", q, "page", page);
    }

    /**
     * Deliberately expensive: a cross join over generated ranges, so the slow
     * query capture has something real to catch. The divisor changes per call —
     * with a constant one H2 answers the repeat executions from cache and the
     * endpoint stops being slow after the first request.
     */
    @LoadTest
    @GetMapping("/report")
    public Map<String, Object> report() {
        int divisor = ThreadLocalRandom.current().nextInt(3, 97);
        Long matches = jdbc.queryForObject(
                "select count(*) from system_range(1, 40000) a, system_range(1, 40) b "
                        + "where mod(a.x + b.x, ?) = 0",
                Long.class, divisor);
        return Map.of("divisor", divisor, "matches", matches);
    }

    // Intentionally NOT annotated — must not appear in the /loadmin list.
    @GetMapping("/plain")
    public Map<String, String> plain() {
        return Map.of("message", "not a load test target");
    }

    private void simulateWork() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50));
    }
}
