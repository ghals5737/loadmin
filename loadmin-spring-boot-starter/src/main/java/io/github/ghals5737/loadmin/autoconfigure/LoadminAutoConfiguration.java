package io.github.ghals5737.loadmin.autoconfigure;

import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ghals5737.loadmin.core.LoadTestEndpointScanner;
import io.github.ghals5737.loadmin.core.engine.LoadTestEngine;
import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.LoadTestRunRegistry;
import io.github.ghals5737.loadmin.core.history.RunHistoryStore;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Auto-configuration for loadmin.
 *
 * <p>Requires an explicit {@code loadmin.enabled=true} opt-in: loadmin drives
 * load against the running application, so nothing is registered by default.
 * This is a deliberate guard against enabling it in production by accident.
 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "loadmin", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LoadminProperties.class)
public class LoadminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoadTestEndpointScanner loadTestEndpointScanner(
            // Qualified by name: actuator registers a second RequestMappingHandlerMapping
            // (controllerEndpointHandlerMapping) that must not be scanned.
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new LoadTestEndpointScanner(requestMappingHandlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadTestRunRegistry loadTestRunRegistry() {
        return new LoadTestRunRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnProperty(prefix = "loadmin.history", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RunHistoryStore runHistoryStore(LoadminProperties properties,
            ObjectProvider<ObjectMapper> objectMapper) {
        LoadminProperties.History history = properties.getHistory();
        // The application's own mapper, so stored runs look exactly like the
        // ones the REST API serves.
        return new RunHistoryStore(Paths.get(history.getDir()), history.getMaxRuns(),
                objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadTestEngine loadTestEngine(LoadTestRunRegistry registry, LoadminProperties properties,
            Environment environment, ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<RunHistoryStore> historyStore) {
        // Resolved lazily: local.server.port is only available once the web
        // server has started, which is after this bean is created.
        Supplier<String> baseUrl = () -> {
            String port = environment.getProperty("local.server.port",
                    environment.getProperty("server.port", "8080"));
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            return "http://localhost:" + port + contextPath;
        };
        RunHistoryStore history = historyStore.getIfAvailable();
        Consumer<LoadTestRun> onFinished = history == null ? null : run -> history.save(run.view());
        return new LoadTestEngine(registry, baseUrl, meterRegistry.getIfAvailable(),
                properties.getMaxConcurrency(), properties.getMaxDurationSeconds(), onFinished);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadminEndpointController loadminEndpointController(LoadTestEndpointScanner scanner) {
        return new LoadminEndpointController(scanner);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadminRunController loadminRunController(LoadTestEngine engine,
            LoadTestRunRegistry registry, LoadTestEndpointScanner scanner) {
        return new LoadminRunController(engine, registry, scanner);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RunHistoryStore.class)
    public LoadminHistoryController loadminHistoryController(RunHistoryStore history,
            LoadTestRunRegistry registry) {
        return new LoadminHistoryController(history, registry);
    }
}
