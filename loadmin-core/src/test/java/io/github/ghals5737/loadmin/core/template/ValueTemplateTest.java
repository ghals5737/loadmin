package io.github.ghals5737.loadmin.core.template;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.ghals5737.loadmin.core.template.ValueTemplate.Mode;

import org.junit.jupiter.api.Test;

class ValueTemplateTest {

    @Test
    void templateWithoutPlaceholdersRendersItself() {
        ValueTemplate template = ValueTemplate.compile("/api/hello", Mode.PATH);

        assertFalse(template.dynamic());
        assertEquals("/api/hello", template.render());
        assertEquals("/api/hello", template.source());
    }

    @Test
    void doubleDollarRendersALiteralDollar() {
        assertEquals("$5", ValueTemplate.compile("$$5", Mode.BODY).render());
        assertEquals("${x}", ValueTemplate.compile("$${x}", Mode.BODY).render());
    }

    @Test
    void intStaysWithinItsBounds() {
        ValueTemplate template = ValueTemplate.compile("/api/users/${int(1,20)}", Mode.PATH);

        assertTrue(template.dynamic());
        for (int i = 0; i < 500; i++) {
            long id = Long.parseLong(template.render().substring("/api/users/".length()));
            assertTrue(id >= 1 && id <= 20, "out of range: " + id);
        }
    }

    @Test
    void intAcceptsASingleValueRange() {
        assertEquals("7", ValueTemplate.compile("${int(7,7)}", Mode.PATH).render());
    }

    @Test
    void seqIncrementsAndIsSharedAcrossThreads() throws Exception {
        ValueTemplate template = ValueTemplate.compile("${seq}", Mode.PATH);
        int threads = 8;
        int perThread = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ConcurrentLinkedQueue<String> rendered = new ConcurrentLinkedQueue<>();
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        rendered.add(template.render());
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        Set<String> unique = new HashSet<>(rendered);
        assertEquals(threads * perThread, unique.size(), "sequence handed out a duplicate");
        assertTrue(unique.contains("1"));
        assertTrue(unique.contains(Integer.toString(threads * perThread)));
    }

    @Test
    void seqCanStartAtAGivenValue() {
        ValueTemplate template = ValueTemplate.compile("${seq(1000)}", Mode.PATH);

        assertEquals("1000", template.render());
        assertEquals("1001", template.render());
    }

    @Test
    void cycleStaysInRangeAndCoversItEvenly() {
        ValueTemplate template = ValueTemplate.compile("${cycle(10,14)}", Mode.PATH);
        List<String> rendered = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rendered.add(template.render());
        }

        // Walks the range in order and wraps: every key used exactly four times.
        assertEquals(List.of("10", "11", "12", "13", "14"), rendered.subList(0, 5));
        assertEquals(List.of("10", "11", "12", "13", "14"), rendered.subList(5, 10));
        Map<String, Long> counts = rendered.stream()
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        assertEquals(Set.of(4L), Set.copyOf(counts.values()));
    }

    @Test
    void cycleOfASingleValueAlwaysRendersIt() {
        ValueTemplate template = ValueTemplate.compile("${cycle(7,7)}", Mode.PATH);

        assertEquals("7", template.render());
        assertEquals("7", template.render());
    }

    @Test
    void cycleIsSharedAcrossThreads() throws Exception {
        ValueTemplate template = ValueTemplate.compile("${cycle(1,4)}", Mode.PATH);
        int threads = 8;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ConcurrentLinkedQueue<String> rendered = new ConcurrentLinkedQueue<>();
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        rendered.add(template.render());
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        Map<String, Long> counts = rendered.stream()
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        // 1600 renders over four keys, one counter: 400 each, no drift.
        assertEquals(Set.of(400L), Set.copyOf(counts.values()));
    }

    @Test
    void pickChoosesFromTheGivenValues() {
        ValueTemplate template = ValueTemplate.compile("${pick(seoul|busan|jeju)}", Mode.PATH);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            seen.add(template.render());
        }

        assertEquals(Set.of("seoul", "busan", "jeju"), seen);
    }

    @Test
    void alphaRendersRequestedLength() {
        ValueTemplate template = ValueTemplate.compile("${alpha(8)}", Mode.PATH);
        String value = template.render();

        assertEquals(8, value.length());
        assertTrue(value.matches("[a-z0-9]{8}"), value);
    }

    @Test
    void placeholdersCanBeCombinedWithLiterals() {
        ValueTemplate template = ValueTemplate.compile(
                "{\"id\":${int(1,1)},\"city\":\"${pick(seoul)}\"}", Mode.BODY);

        assertEquals("{\"id\":1,\"city\":\"seoul\"}", template.render());
    }

    @Test
    void everyRenderOfAQueryTemplateStaysOnOneSegment() {
        ValueTemplate template = ValueTemplate.compile(
                "/api/search?q=${pick(shoes|bags)}&page=${int(1,5)}", Mode.PATH);

        for (int i = 0; i < 200; i++) {
            String rendered = template.render();
            assertTrue(rendered.matches("/api/search\\?q=(shoes|bags)&page=[1-5]"), rendered);
        }
    }

    @Test
    void pathModeRejectsChoicesThatCouldEscapeTheEndpoint() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("/api/x/${pick(a|../admin)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("/api/x/${pick(a|b?c=1)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("/api/x/${pick(a|%2Fadmin)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("/api/x/${pick(a|b c)}", Mode.PATH)));
    }

    @Test
    void bodyModeAllowsAnyChoice() {
        ValueTemplate template = ValueTemplate.compile("${pick(a/b)}", Mode.BODY);

        assertEquals("a/b", template.render());
    }

    @Test
    void malformedTemplatesAreRejected() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("/api/users/${int(1,20)", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("/api/users/${}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("/api/users/${nope}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${int(5,1)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${int(1)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${int(a,b)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${cycle(5,1)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${cycle(1)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${alpha(0)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${alpha(9999)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${uuid(1)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${pick()}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile("${pick(a|)}", Mode.PATH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValueTemplate.compile(null, Mode.PATH)));
    }

    @Test
    void unknownGeneratorNamesTheSupportedOnes() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ValueTemplate.compile("${random}", Mode.PATH));

        assertTrue(error.getMessage().contains("int(min,max)"), error.getMessage());
    }
}
