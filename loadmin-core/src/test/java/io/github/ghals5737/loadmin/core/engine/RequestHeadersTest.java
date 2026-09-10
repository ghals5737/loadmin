package io.github.ghals5737.loadmin.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RequestHeadersTest {

    @Test
    void namesAndValuesAreTrimmed() {
        Map<String, String> headers = RequestHeaders.checked(
                Map.of("  Authorization ", "  Bearer abc  "));

        assertEquals(Map.of("Authorization", "Bearer abc"), headers);
    }

    @Test
    void nothingIsAlsoFine() {
        assertEquals(Map.of(), RequestHeaders.checked(null));
        assertEquals(Map.of(), RequestHeaders.checked(Map.of()));
    }

    @Test
    void aValueMayNotSmuggleFurtherHeaders() {
        Map<String, String> injected = Map.of("X-Trace", "a\r\nX-Admin: true");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RequestHeaders.checked(injected));
        assertTrue(error.getMessage().contains("line break"), error.getMessage());
    }

    @Test
    void aNameHasToBeAName() {
        assertThrows(IllegalArgumentException.class,
                () -> RequestHeaders.checked(Map.of("X Trace", "1")));
        Map<String, String> blank = new HashMap<>();
        blank.put("  ", "1");
        assertThrows(IllegalArgumentException.class, () -> RequestHeaders.checked(blank));
    }

    @Test
    void tooManyHeadersAreRejected() {
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i < 21; i++) {
            many.put("X-Header-" + i, "value");
        }

        assertThrows(IllegalArgumentException.class, () -> RequestHeaders.checked(many));
    }
}
