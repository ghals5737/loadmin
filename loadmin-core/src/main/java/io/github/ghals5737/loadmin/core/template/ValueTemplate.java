package io.github.ghals5737.loadmin.core.template;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A request path or body with {@code ${...}} placeholders that are re-rendered
 * for every request, so virtual users do not all hit the same row or key.
 *
 * <p>Syntax: {@code ${generator}} or {@code ${generator(args)}}; {@code $$}
 * renders a literal {@code $}. A placeholder ends at the first {@code }}.
 * Supported generators: {@code int(min,max)}, {@code seq}, {@code seq(start)},
 * {@code uuid}, {@code alpha(length)}, {@code pick(a|b|c)}, {@code now}.
 *
 * <p>Compile once per run, render per request: parsing happens up front and
 * rendering only walks the parsed parts. {@code seq} counters live in the
 * compiled instance, so all virtual users of a run share one sequence.
 * Randomness comes from {@link ThreadLocalRandom}, so the virtual-user threads
 * never contend on it.
 */
public final class ValueTemplate {

    /** Where a template is used, which decides what generated values may contain. */
    public enum Mode {
        /**
         * Request path (and query string). Generated values must not be able to
         * change which endpoint is hit, so they may not contain path, query or
         * escaping characters. Literal text typed by the user is unrestricted.
         */
        PATH,
        /** Request body. Generated values are unrestricted. */
        BODY
    }

    private static final String PATH_FORBIDDEN = "/?#&=%;\\";
    private static final char[] ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int MAX_ALPHA_LENGTH = 256;
    private static final String SUPPORTED = "int(min,max), seq, seq(start), uuid, alpha(length), pick(a|b|c), now";

    /** A generator that a placeholder can name. */
    public enum Kind {
        INT, SEQ, UUID, ALPHA, PICK, NOW
    }

    /** One piece of a parsed template: fixed text, or a value to generate. */
    public sealed interface Part permits Literal, Placeholder {
    }

    /** Text that is copied through as it is. */
    public record Literal(String text) implements Part {
    }

    /**
     * A {@code ${...}} to generate a value for. Arguments are the parsed,
     * validated form: {@code INT} carries two bounds, {@code PICK} its choices,
     * {@code SEQ} its start (possibly empty), the rest nothing.
     */
    public record Placeholder(Kind kind, List<String> args) implements Part {
    }

    private final String source;
    private final String constant;
    private final List<Part> parts;
    private final Supplier<String>[] generators;

    private ValueTemplate(String source, String constant, List<Part> parts,
            Supplier<String>[] generators) {
        this.source = source;
        this.constant = constant;
        this.parts = parts;
        this.generators = generators;
    }

    /**
     * Parses {@code source}, rejecting unknown generators, malformed arguments
     * and — in {@link Mode#PATH} — choices that could escape the endpoint.
     *
     * @throws IllegalArgumentException if the template cannot be parsed
     */
    @SuppressWarnings("unchecked")
    public static ValueTemplate compile(String source, Mode mode) {
        if (source == null) {
            throw new IllegalArgumentException("template must not be null");
        }
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        boolean dynamic = false;
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '$' && i + 1 < source.length() && source.charAt(i + 1) == '$') {
                literal.append('$');
                i += 2;
                continue;
            }
            if (c == '$' && i + 1 < source.length() && source.charAt(i + 1) == '{') {
                int end = source.indexOf('}', i + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("unclosed ${ in template: " + source);
                }
                if (literal.length() > 0) {
                    parts.add(new Literal(literal.toString()));
                    literal.setLength(0);
                }
                parts.add(placeholder(source.substring(i + 2, end).trim(), mode));
                dynamic = true;
                i = end + 1;
                continue;
            }
            literal.append(c);
            i++;
        }
        if (!dynamic) {
            return new ValueTemplate(source, literal.toString(), List.of(), null);
        }
        if (literal.length() > 0) {
            parts.add(new Literal(literal.toString()));
        }
        Supplier<String>[] generators = new Supplier[parts.size()];
        for (int part = 0; part < parts.size(); part++) {
            generators[part] = renderer(parts.get(part));
        }
        return new ValueTemplate(source, null, List.copyOf(parts), generators);
    }

    /**
     * The parsed template. Empty for a template without placeholders — that one
     * renders {@link #source} unchanged. Exporters walk this to translate a
     * template into another runner's language.
     */
    public List<Part> parts() {
        return parts;
    }

    /** Renders one concrete value. Called once per request, from many threads. */
    public String render() {
        if (generators == null) {
            return constant;
        }
        StringBuilder out = new StringBuilder(source.length() + 16);
        for (Supplier<String> generator : generators) {
            out.append(generator.get());
        }
        return out.toString();
    }

    /** Whether the template contains at least one placeholder. */
    public boolean dynamic() {
        return generators != null;
    }

    /** The template as it was typed, for display and round-tripping. */
    public String source() {
        return source;
    }

    /** Parses and validates one {@code ${...}} into its structured form. */
    private static Placeholder placeholder(String expr, Mode mode) {
        if (expr.isEmpty()) {
            throw new IllegalArgumentException("empty ${} in template");
        }
        String name = expr;
        String args = "";
        boolean hasArgs = false;
        int open = expr.indexOf('(');
        if (open >= 0) {
            if (!expr.endsWith(")")) {
                throw new IllegalArgumentException("unclosed ( in ${" + expr + "}");
            }
            name = expr.substring(0, open).trim();
            args = expr.substring(open + 1, expr.length() - 1);
            hasArgs = true;
        }
        switch (name) {
            case "uuid":
                requireNoArgs(expr, hasArgs);
                return new Placeholder(Kind.UUID, List.of());
            case "now":
                requireNoArgs(expr, hasArgs);
                return new Placeholder(Kind.NOW, List.of());
            case "seq": {
                long start = hasArgs && !args.isBlank() ? parseLong(args, expr) : 1L;
                return new Placeholder(Kind.SEQ, List.of(Long.toString(start)));
            }
            case "int": {
                String[] bounds = hasArgs ? args.split(",", -1) : new String[0];
                if (bounds.length != 2) {
                    throw new IllegalArgumentException(
                            "${" + expr + "} needs two bounds, e.g. ${int(1,20)}");
                }
                long min = parseLong(bounds[0], expr);
                long max = parseLong(bounds[1], expr);
                if (min > max) {
                    throw new IllegalArgumentException(
                            "${" + expr + "}: lower bound must not be greater than upper bound");
                }
                if (max == Long.MAX_VALUE) {
                    throw new IllegalArgumentException("${" + expr + "}: upper bound is too large");
                }
                return new Placeholder(Kind.INT, List.of(Long.toString(min), Long.toString(max)));
            }
            case "alpha": {
                long length = hasArgs && !args.isBlank() ? parseLong(args, expr) : 0L;
                if (length < 1 || length > MAX_ALPHA_LENGTH) {
                    throw new IllegalArgumentException(
                            "${" + expr + "} needs a length between 1 and " + MAX_ALPHA_LENGTH);
                }
                return new Placeholder(Kind.ALPHA, List.of(Long.toString(length)));
            }
            case "pick": {
                if (!hasArgs) {
                    throw new IllegalArgumentException(
                            "${" + expr + "} needs choices, e.g. ${pick(a|b|c)}");
                }
                String[] choices = args.split("\\|", -1);
                for (String choice : choices) {
                    if (choice.isEmpty()) {
                        throw new IllegalArgumentException("${" + expr + "}: empty choice");
                    }
                    if (mode == Mode.PATH) {
                        requirePathSafe(choice, expr);
                    }
                }
                return new Placeholder(Kind.PICK, List.of(choices));
            }
            default:
                throw new IllegalArgumentException(
                        "unknown generator ${" + expr + "}; supported: " + SUPPORTED);
        }
    }

    /** Builds the per-request renderer for one parsed part. */
    private static Supplier<String> renderer(Part part) {
        if (part instanceof Literal literal) {
            String text = literal.text();
            return () -> text;
        }
        Placeholder placeholder = (Placeholder) part;
        List<String> args = placeholder.args();
        switch (placeholder.kind()) {
            case UUID:
                return () -> java.util.UUID.randomUUID().toString();
            case NOW:
                return () -> Long.toString(System.currentTimeMillis());
            case SEQ: {
                AtomicLong counter = new AtomicLong(Long.parseLong(args.get(0)));
                return () -> Long.toString(counter.getAndIncrement());
            }
            case INT: {
                long min = Long.parseLong(args.get(0));
                long max = Long.parseLong(args.get(1));
                return () -> Long.toString(ThreadLocalRandom.current().nextLong(min, max + 1));
            }
            case ALPHA: {
                int size = Integer.parseInt(args.get(0));
                return () -> randomAlphanumeric(size);
            }
            case PICK: {
                String[] choices = args.toArray(new String[0]);
                return () -> choices[ThreadLocalRandom.current().nextInt(choices.length)];
            }
            default:
                throw new IllegalStateException("unhandled generator " + placeholder.kind());
        }
    }

    private static void requireNoArgs(String expr, boolean hasArgs) {
        if (hasArgs) {
            throw new IllegalArgumentException("${" + expr + "} takes no arguments");
        }
    }

    private static long parseLong(String text, String expr) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "${" + expr + "}: '" + text.trim() + "' is not a number");
        }
    }

    /**
     * Keeps a generated value inside a single path segment: no separators, no
     * query or fragment start, no percent escapes, no matrix parameters.
     */
    private static void requirePathSafe(String value, String expr) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= ' ' || c == 127 || PATH_FORBIDDEN.indexOf(c) >= 0) {
                throw new IllegalArgumentException("${" + expr + "}: choice '" + value
                        + "' may not be used in a path (forbidden character '" + c + "')");
            }
        }
    }

    private static String randomAlphanumeric(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ALPHANUM[random.nextInt(ALPHANUM.length)];
        }
        return new String(chars);
    }
}
