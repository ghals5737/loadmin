package io.github.ghals5737.loadmin.core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScriptExporterTest {

    private final K6ScriptExporter k6 = new K6ScriptExporter();
    private final GatlingScriptExporter gatling = new GatlingScriptExporter();

    private static ExportRequest request(String method, String pattern, String path, String body) {
        return new ExportRequest("http://localhost:8080", method, pattern, path, body, 20, 8);
    }

    @Test
    void k6CarriesTheLoadSettings() {
        String script = k6.render(request("GET", "/api/hello", "/api/hello", null));

        assertTrue(script.contains("vus: 20,"), script);
        assertTrue(script.contains("duration: '8s',"), script);
        assertTrue(script.contains("const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';"), script);
    }

    @Test
    void k6TranslatesEveryGenerator() {
        String script = k6.render(request("GET", "/api/x",
                "/api/x/${int(1,20)}/${uuid}/${alpha(6)}/${pick(a|b)}/${now}/${seq}", null));

        assertTrue(script.contains("randomInt(1, 20)"), script);
        assertTrue(script.contains("uuid()"), script);
        assertTrue(script.contains("randomAlpha(6)"), script);
        assertTrue(script.contains("pick(['a', 'b'])"), script);
        assertTrue(script.contains("Date.now()"), script);
        assertTrue(script.contains("seq_1_1++"), script);
    }

    @Test
    void k6OnlyDefinesTheHelpersItUses() {
        String script = k6.render(request("GET", "/api/users/{id}", "/api/users/${int(1,20)}", null));

        assertTrue(script.contains("function randomInt"), script);
        assertFalse(script.contains("function uuid"), script);
        assertFalse(script.contains("function pick"), script);
        assertFalse(script.contains("function randomAlpha"), script);
    }

    @Test
    void k6SaysWhatHappensToSeq() {
        String withSeq = k6.render(request("GET", "/api/x", "/api/x/${seq(1000)}", null));
        String withoutSeq = k6.render(request("GET", "/api/x", "/api/x/1", null));

        assertTrue(withSeq.contains("let seq_1_1000 = 1000 + (__VU - 1) * 1000000;"), withSeq);
        assertTrue(withSeq.contains("its own JS runtime"), withSeq);
        assertFalse(withoutSeq.contains("__VU"), withoutSeq);
    }

    @Test
    void k6SendsABodyWithItsContentType() {
        String script = k6.render(request("POST", "/api/orders", "/api/orders",
                "{\"item\": \"${pick(shoes)}\"}"));

        assertTrue(script.contains("'Content-Type': 'application/json'"), script);
        assertTrue(script.contains("http.request('POST', url, body, params)"), script);
        // The JSON's double quotes survive into a single-quoted JS literal.
        assertTrue(script.contains("'{\"item\": \"' + pick(['shoes']) + '\"}'"), script);
    }

    @Test
    void k6EscapesQuotesInsideLiterals() {
        String script = k6.render(request("POST", "/api/orders", "/api/orders",
                "{\"note\": \"it's ${alpha(3)}\"}"));

        assertTrue(script.contains("it\\'s "), script);
    }

    @Test
    void gatlingUsesAClosedInjectionProfile() {
        String script = gatling.render(request("GET", "/api/hello", "/api/hello", null));

        assertTrue(script.contains("constantConcurrentUsers(20).during(8)"), script);
        assertTrue(script.contains("System.getProperty(\"baseUrl\", \"http://localhost:8080\")"), script);
        assertTrue(script.contains(".get(session -> \"/api/hello\")"), script);
    }

    @Test
    void gatlingMakesTheIntBoundInclusive() {
        String script = gatling.render(request("GET", "/api/users/{id}", "/api/users/${int(1,20)}", null));

        // loadmin's upper bound is inclusive, nextLong's is not.
        assertTrue(script.contains("nextLong(1L, 21L)"), script);
    }

    @Test
    void gatlingKeepsSeqShared() {
        String script = gatling.render(request("GET", "/api/x", "/api/x/${seq(500)}", null));

        assertTrue(script.contains("AtomicLong SEQ_1_500 = new AtomicLong(500L)"), script);
        assertTrue(script.contains("SEQ_1_500.getAndIncrement()"), script);
    }

    @Test
    void gatlingImportsOnlyWhatItNeeds() {
        String plain = gatling.render(request("GET", "/api/hello", "/api/hello", null));
        String full = gatling.render(request("GET", "/api/x", "/api/x/${uuid}/${seq}", null));

        assertFalse(plain.contains("import java.util.UUID;"), plain);
        assertFalse(plain.contains("AtomicLong"), plain);
        assertTrue(full.contains("import java.util.UUID;"), full);
        assertTrue(full.contains("import java.util.concurrent.atomic.AtomicLong;"), full);
    }

    @Test
    void gatlingKeepsAConcatenationAString() {
        String script = gatling.render(request("POST", "/api/orders", "/api/orders", "${uuid}"));

        // A body that starts with a placeholder would otherwise not be a String.
        assertTrue(script.contains("StringBody(session -> \"\" + UUID.randomUUID())"), script);
    }

    @Test
    void fileNamesComeFromTheTarget() {
        ExportRequest request = request("GET", "/api/users/{id}", "/api/users/1", null);

        assertEquals("loadmin-api-users-id.js", k6.fileName(request));
        assertEquals("LoadminSimulation.java", gatling.fileName(request));
        assertEquals("root", ScriptExporter.slug("/"));
    }

    @Test
    void amalformedTemplateIsRejectedBeforeAnythingIsWritten() {
        assertThrows(IllegalArgumentException.class,
                () -> k6.render(request("GET", "/api/x", "/api/x/${nope}", null)));
        assertThrows(IllegalArgumentException.class,
                () -> gatling.render(request("GET", "/api/x", "/api/x/${pick(a|../b)}", null)));
    }
}
