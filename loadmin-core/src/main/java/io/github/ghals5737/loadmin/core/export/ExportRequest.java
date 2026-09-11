package io.github.ghals5737.loadmin.core.export;

import java.util.List;
import java.util.Map;

import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;

/**
 * What a generated script has to reproduce: the scenario, and the load to put
 * on it.
 *
 * @param baseUrl address of the application under test, used as the script's
 *                default target
 * @param spec    the steps, the concurrency and the duration
 * @param headers sent with every request; secrets among them are read from the
 *                environment by the generated script rather than written into it
 */
public record ExportRequest(String baseUrl, LoadTestSpec spec, Map<String, String> headers,
        Map<String, List<String>> valueLists) {

    public ExportRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        valueLists = valueLists == null ? Map.of() : Map.copyOf(valueLists);
    }

    public ExportRequest(String baseUrl, LoadTestSpec spec) {
        this(baseUrl, spec, Map.of(), Map.of());
    }

    public ExportRequest(String baseUrl, LoadTestSpec spec, Map<String, String> headers) {
        this(baseUrl, spec, headers, Map.of());
    }

    /**
     * Whether a header's value should be kept out of the generated file.
     * Generated scripts get committed; bearer tokens should not travel with
     * them.
     */
    public static boolean secret(String name) {
        String lower = name.toLowerCase();
        return lower.equals("authorization") || lower.equals("cookie")
                || lower.contains("token") || lower.contains("secret")
                || lower.contains("password") || lower.contains("key");
    }

    /** The environment variable a secret header is read from. */
    public static String variable(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }
}
