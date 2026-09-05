package io.github.ghals5737.loadmin.autoconfigure;

import java.util.function.Supplier;

import org.springframework.core.env.Environment;

/**
 * Where the application can be reached from inside itself.
 *
 * <p>Resolved lazily: {@code local.server.port} only exists once the web server
 * has started, which is after the beans that need this are created.
 */
class BaseUrlSupplier implements Supplier<String> {

    private final Environment environment;

    BaseUrlSupplier(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String get() {
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        return "http://localhost:" + port + contextPath;
    }
}
