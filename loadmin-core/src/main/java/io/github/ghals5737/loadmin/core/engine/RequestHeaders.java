package io.github.ghals5737.loadmin.core.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headers a run sends with every request, usually the bearer token an API
 * needs before it does anything but return 401.
 *
 * <p>They are checked, not stored: a value carrying a newline could append
 * headers of its own, so it is rejected rather than sanitised.
 */
public final class RequestHeaders {

    private static final int MAX_HEADERS = 20;

    private RequestHeaders() {
    }

    /**
     * @throws IllegalArgumentException if a name or value cannot be sent safely
     */
    public static Map<String, String> checked(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        if (headers.size() > MAX_HEADERS) {
            throw new IllegalArgumentException("at most " + MAX_HEADERS + " headers");
        }
        Map<String, String> checked = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("a header needs a name");
            }
            if (!trimmed.chars().allMatch(RequestHeaders::nameCharacter)) {
                throw new IllegalArgumentException("not a usable header name: " + trimmed);
            }
            String content = value == null ? "" : value.trim();
            if (content.indexOf('\n') >= 0 || content.indexOf('\r') >= 0) {
                throw new IllegalArgumentException(
                        "header " + trimmed + " may not contain a line break");
            }
            checked.put(trimmed, content);
        });
        return Map.copyOf(checked);
    }

    /** RFC 7230 token characters, the ones a header name may use. */
    private static boolean nameCharacter(int c) {
        return Character.isLetterOrDigit(c) || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }
}
