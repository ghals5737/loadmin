package io.github.ghals5737.loadmin.autoconfigure;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ghals5737.loadmin.core.LoadTestEndpointScanner;
import io.github.ghals5737.loadmin.core.engine.LoadTestEngine;
import io.github.ghals5737.loadmin.core.engine.LoadTestRunRegistry;
import io.github.ghals5737.loadmin.core.engine.RunListener;
import io.github.ghals5737.loadmin.core.export.GatlingScriptExporter;
import io.github.ghals5737.loadmin.core.export.K6ScriptExporter;
import io.github.ghals5737.loadmin.core.export.ScriptExporter;
import io.github.ghals5737.loadmin.core.history.RunHistoryStore;
import io.github.ghals5737.loadmin.core.query.SlowQueryDataSource;
import io.github.ghals5737.loadmin.core.query.SlowQueryRecorder;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
    @ConditionalOnProperty(prefix = "loadmin.slow-query", name = "enabled", havingValue = "true")
    public SlowQueryRecorder loadminSlowQueryRecorder(LoadminProperties properties) {
        return new SlowQueryRecorder(properties.getSlowQuery().getThreshold().toMillis());
    }

    /**
     * Wraps the application's DataSource so statements can be timed during a
     * run. Static, and resolving the recorder lazily, so declaring a
     * BeanPostProcessor does not drag the rest of the configuration into being
     * created too early.
     */
    @Bean
    // Deliberately a property condition, not @ConditionalOnBean: a
    // BeanPostProcessor is created before ordinary beans are registered, so a
    // bean condition here evaluates too early to be trustworthy.
    @ConditionalOnProperty(prefix = "loadmin.slow-query", name = "enabled", havingValue = "true")
    static BeanPostProcessor loadminSlowQueryDataSourcePostProcessor(
            ObjectProvider<SlowQueryRecorder> recorder) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource dataSource)) {
                    return bean;
                }
                SlowQueryRecorder found = recorder.getIfAvailable();
                return found == null ? bean : SlowQueryDataSource.wrap(dataSource, found);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public TargetValidator loadminTargetValidator(LoadTestEndpointScanner scanner) {
        return new TargetValidator(scanner);
    }

    @Bean
    @ConditionalOnMissingBean(name = "loadminBaseUrl")
    public Supplier<String> loadminBaseUrl(Environment environment) {
        return new BaseUrlSupplier(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public K6ScriptExporter k6ScriptExporter() {
        return new K6ScriptExporter();
    }

    @Bean
    @ConditionalOnMissingBean
    public GatlingScriptExporter gatlingScriptExporter() {
        return new GatlingScriptExporter();
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
            ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<RunHistoryStore> historyStore,
            ObjectProvider<SlowQueryRecorder> slowQueryRecorder,
            @Qualifier("loadminBaseUrl") Supplier<String> baseUrl) {
        List<RunListener> listeners = new ArrayList<>();
        RunHistoryStore history = historyStore.getIfAvailable();
        if (history != null) {
            listeners.add(new HistoryRunListener(history));
        }
        SlowQueryRecorder recorder = slowQueryRecorder.getIfAvailable();
        if (recorder != null) {
            listeners.add(new SlowQueryRunListener(recorder));
        }
        return new LoadTestEngine(registry, baseUrl, meterRegistry.getIfAvailable(),
                properties.getMaxConcurrency(), properties.getMaxDurationSeconds(), listeners);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadminEndpointController loadminEndpointController(LoadTestEndpointScanner scanner) {
        return new LoadminEndpointController(scanner);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadminRunController loadminRunController(LoadTestEngine engine,
            LoadTestRunRegistry registry, TargetValidator targets) {
        return new LoadminRunController(engine, registry, targets);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadminExportController loadminExportController(List<ScriptExporter> exporters,
            TargetValidator targets, @Qualifier("loadminBaseUrl") Supplier<String> baseUrl) {
        return new LoadminExportController(exporters, targets, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RunHistoryStore.class)
    public LoadminHistoryController loadminHistoryController(RunHistoryStore history,
            LoadTestRunRegistry registry) {
        return new LoadminHistoryController(history, registry);
    }
}
