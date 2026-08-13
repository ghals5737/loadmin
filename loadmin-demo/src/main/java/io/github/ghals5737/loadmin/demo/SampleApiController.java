package io.github.ghals5737.loadmin.demo;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.github.ghals5737.loadmin.core.LoadTest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SampleApiController {

    @LoadTest
    @GetMapping("/hello")
    public Map<String, String> hello() throws InterruptedException {
        simulateWork();
        return Map.of("message", "hello");
    }

    @LoadTest
    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable long id) throws InterruptedException {
        simulateWork();
        return Map.of("id", id, "name", "user-" + id);
    }

    @LoadTest
    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> order)
            throws InterruptedException {
        simulateWork();
        return Map.of("status", "created", "order", order);
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
