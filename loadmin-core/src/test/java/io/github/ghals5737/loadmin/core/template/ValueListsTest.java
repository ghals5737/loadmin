package io.github.ghals5737.loadmin.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ghals5737.loadmin.core.template.ValueTemplate.Mode;

import org.junit.jupiter.api.Test;

class ValueListsTest {

    private static final Map<String, List<String>> IDS =
            Map.of("ids", List.of("hg240901-a123459", "hg240902-b234567", "hg240903-c345678"));

    @Test
    void pickDrawsFromTheList() {
        ValueTemplate template = ValueTemplate.compile(
                "/api/examinees/${pick(@ids)}", Mode.PATH, IDS);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(template.render());
        }

        assertEquals(Set.of("/api/examinees/hg240901-a123459",
                "/api/examinees/hg240902-b234567",
                "/api/examinees/hg240903-c345678"), seen);
    }

    @Test
    void cycleWalksTheListInOrder() {
        ValueTemplate template = ValueTemplate.compile("${cycle(@ids)}", Mode.PATH, IDS);
        List<String> rendered = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rendered.add(template.render());
        }

        assertEquals(List.of("hg240901-a123459", "hg240902-b234567", "hg240903-c345678",
                "hg240901-a123459", "hg240902-b234567", "hg240903-c345678",
                "hg240901-a123459"), rendered);
    }

    @Test
    void aListWorksInABodyToo() {
        ValueTemplate template = ValueTemplate.compile(
                "{\"examinee\": \"${cycle(@ids)}\"}", Mode.BODY, IDS);

        assertEquals("{\"examinee\": \"hg240901-a123459\"}", template.render());
    }

    @Test
    void anUnknownListNamesTheOnesThatExist() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ValueTemplate.compile("${pick(@nope)}", Mode.PATH, IDS));

        assertTrue(error.getMessage().contains("no value list named 'nope'"), error.getMessage());
        assertTrue(error.getMessage().contains("ids"), error.getMessage());
    }

    @Test
    void aListValueGetsTheSameCheckAsAWrittenChoice() {
        Map<String, List<String>> escaping = Map.of("ids", List.of("7", "../plain"));

        // In a path it could leave the endpoint; in a body it is just a string.
        assertThrows(IllegalArgumentException.class,
                () -> ValueTemplate.compile("/api/users/${pick(@ids)}", Mode.PATH, escaping));
        assertTrue(Set.of("7", "../plain").contains(
                ValueTemplate.compile("${pick(@ids)}", Mode.BODY, escaping).render()));
    }

    @Test
    void withoutAnAtSignTheArgumentIsStillALiteral() {
        // ${pick(ids)} meant "the string ids" before value lists existed.
        ValueTemplate template = ValueTemplate.compile("${pick(ids)}", Mode.PATH, IDS);

        assertEquals("ids", template.render());
    }

    @Test
    void listsAreCheckedBeforeUse() {
        assertThrows(IllegalArgumentException.class,
                () -> ValueLists.checked(Map.of("bad name", List.of("a"))));
        assertThrows(IllegalArgumentException.class,
                () -> ValueLists.checked(Map.of("ids", List.of())));
        assertEquals(Map.of(), ValueLists.checked(null));
        // Blank entries are dropped rather than sent as empty path segments.
        assertEquals(Map.of("ids", List.of("a", "b")),
                ValueLists.checked(Map.of("ids", List.of(" a ", "", "b"))));
    }
}
