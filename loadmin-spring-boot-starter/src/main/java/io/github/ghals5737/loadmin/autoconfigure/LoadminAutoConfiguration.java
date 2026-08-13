package io.github.ghals5737.loadmin.autoconfigure;

import io.github.ghals5737.loadmin.core.LoadTestEndpointScanner;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
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
public class LoadminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoadTestEndpointScanner loadTestEndpointScanner(
            RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new LoadTestEndpointScanner(requestMappingHandlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadminEndpointController loadminEndpointController(LoadTestEndpointScanner scanner) {
        return new LoadminEndpointController(scanner);
    }
}
