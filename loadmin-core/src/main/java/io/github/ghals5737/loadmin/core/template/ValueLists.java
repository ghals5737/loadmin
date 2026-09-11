package io.github.ghals5737.loadmin.core.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named lists of values a run can draw from, so a template can say
 * {@code ${pick(@ids)}} instead of carrying two hundred identifiers.
 *
 * <p>Like request headers, these belong to the run and never to the stored
 * spec: history keeps the reference, not the values. Identifiers pulled out of
 * a real database have no business sitting in a JSON file afterwards.
 */
public final class ValueLists {

    private static final int MAX_LISTS = 200;
    private static final int MAX_VALUES = 10_000;
    private static final int MAX_VALUE_LENGTH = 200;

    private ValueLists() {
    }

    /**
     * @throws IllegalArgumentException if a name is not usable or a list is
     *                                  empty or too large
     */
    public static Map<String, List<String>> checked(Map<String, List<String>> lists) {
        if (lists == null || lists.isEmpty()) {
            return Map.of();
        }
        if (lists.size() > MAX_LISTS) {
            throw new IllegalArgumentException("at most " + MAX_LISTS + " value lists");
        }
        Map<String, List<String>> checked = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, List<String>> entry : lists.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            if (name.isEmpty() || !name.chars().allMatch(ValueLists::nameCharacter)) {
                throw new IllegalArgumentException(
                        "a value list needs a name of letters, digits, - or _: '" + name + "'");
            }
            List<String> values = entry.getValue() == null ? List.of() : entry.getValue().stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (values.isEmpty()) {
                throw new IllegalArgumentException("value list '" + name + "' is empty");
            }
            for (String value : values) {
                if (value.length() > MAX_VALUE_LENGTH) {
                    throw new IllegalArgumentException(
                            "value list '" + name + "' has a value longer than " + MAX_VALUE_LENGTH);
                }
            }
            total += values.size();
            if (total > MAX_VALUES) {
                throw new IllegalArgumentException("at most " + MAX_VALUES + " values in total");
            }
            checked.put(name, List.copyOf(values));
        }
        return Map.copyOf(checked);
    }

    private static boolean nameCharacter(int c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }
}
