package io.github.ghals5737.loadmin.core.export;

/**
 * Turns a load test into a script for a runner that lives outside the
 * application.
 *
 * <p>This is loadmin's answer to its own main caveat: the built-in engine
 * shares a JVM with the application it measures. An exported script moves the
 * load generation out, while loadmin keeps doing what it is actually good at —
 * showing what the server does internally while the load arrives.
 */
public interface ScriptExporter {

    /** Format id used in the API path, e.g. {@code k6}. */
    String id();

    /** Suggested file name for the generated script. */
    String fileName(ExportRequest request);

    /** The script itself. */
    String render(ExportRequest request);

    /**
     * A file name stem derived from the target, e.g. {@code api-users-id} for
     * {@code /api/users/{id}}.
     */
    static String slug(String pathPattern) {
        String slug = pathPattern.replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase();
        return slug.isEmpty() ? "root" : slug;
    }
}
