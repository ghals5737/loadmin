package io.github.ghals5737.loadmin.core.engine;

import java.util.List;
import java.util.Map;

import io.github.ghals5737.loadmin.core.template.ValueLists;

/**
 * What a run needs beyond its spec: the headers to send, and the value lists its
 * templates draw from.
 *
 * <p>Both are deliberately outside {@link LoadTestSpec}. The spec is what gets
 * written to history, and neither a bearer token nor two hundred identifiers
 * pulled from a real database should end up in a file afterwards. Keeping them
 * apart makes that structural instead of something to remember.
 *
 * @param headers    sent with every request
 * @param valueLists named lists a template may reference as {@code ${pick(@name)}}
 */
public record RunOptions(Map<String, String> headers, Map<String, List<String>> valueLists) {

    public RunOptions {
        headers = headers == null ? Map.of() : RequestHeaders.checked(headers);
        valueLists = valueLists == null ? Map.of() : ValueLists.checked(valueLists);
    }

    public static RunOptions none() {
        return new RunOptions(Map.of(), Map.of());
    }
}
