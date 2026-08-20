package io.github.ghals5737.loadmin.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Scans registered MVC handler methods and returns the ones annotated with
 * {@link LoadTest}.
 *
 * <p>Scanning is done on demand (not at startup) so it always reflects the
 * fully initialized handler mappings.
 */
public class LoadTestEndpointScanner {

    private final RequestMappingHandlerMapping handlerMapping;

    public LoadTestEndpointScanner(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public List<LoadTestEndpoint> scan() {
        List<LoadTestEndpoint> endpoints = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            LoadTest annotation = handlerMethod.getMethodAnnotation(LoadTest.class);
            if (annotation == null) {
                return;
            }
            String handler = handlerMethod.getBeanType().getSimpleName()
                    + "#" + handlerMethod.getMethod().getName();
            for (String path : resolvePaths(mapping)) {
                // The annotation's templates are passed through untouched; they are
                // parsed (and rejected) when a run is started, so a typo here never
                // breaks the application's startup.
                String pathTemplate = annotation.path().isBlank() ? path : annotation.path();
                for (String httpMethod : resolveHttpMethods(mapping)) {
                    endpoints.add(new LoadTestEndpoint(httpMethod, path, handler,
                            pathTemplate, annotation.body()));
                }
            }
        });
        endpoints.sort(Comparator.comparing(LoadTestEndpoint::path)
                .thenComparing(LoadTestEndpoint::httpMethod));
        return endpoints;
    }

    private Set<String> resolvePaths(RequestMappingInfo mapping) {
        // PathPatternsCondition is the default in Spring Boot 3; getDirectPaths
        // covers apps switched back to AntPathMatcher.
        if (mapping.getPathPatternsCondition() != null) {
            return mapping.getPathPatternsCondition().getPatternValues();
        }
        return mapping.getDirectPaths();
    }

    private List<String> resolveHttpMethods(RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return List.of("ANY");
        }
        return methods.stream().map(RequestMethod::name).sorted().toList();
    }
}
