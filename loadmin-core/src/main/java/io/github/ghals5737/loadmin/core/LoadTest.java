package io.github.ghals5737.loadmin.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller handler method as a load test target.
 *
 * <p>Annotated handlers are discovered at runtime and listed in the
 * {@code /loadmin} UI, where a load test can be configured and executed
 * against them.
 *
 * <p>{@link #path()} and {@link #body()} carry the request the endpoint expects
 * so the UI can pre-fill it. Both accept {@code ${...}} placeholders
 * (see {@code ValueTemplate}) which are re-rendered for every request:
 *
 * <pre>{@code
 * @LoadTest(path = "/api/users/${int(1,20)}")
 * @GetMapping("/api/users/{id}")
 * }</pre>
 *
 * <p>The values are not validated at startup — a malformed template is reported
 * when a run is started, never by failing the application's boot.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoadTest {

    /**
     * Default request path, with any path variables filled in and optionally a
     * query string. Empty means the mapping pattern is offered as-is.
     */
    String path() default "";

    /**
     * Default JSON request body for {@code POST}/{@code PUT}/{@code PATCH}
     * targets. Empty means no default.
     */
    String body() default "";
}
