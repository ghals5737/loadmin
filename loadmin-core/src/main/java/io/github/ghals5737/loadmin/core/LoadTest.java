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
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoadTest {
}
