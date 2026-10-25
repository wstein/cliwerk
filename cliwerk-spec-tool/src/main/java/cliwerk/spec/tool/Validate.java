package cliwerk.spec.tool;

import java.io.File;

/**
 * Checks that a cliwerk-spec file loads successfully -- {@link SpecLoader#load} already runs
 * every DSL/JSON parsing rule and {@code SpecValidator}'s checks, so this is only about turning a
 * thrown exception into a clear, one-line pass/fail result for a command-line workflow.
 */
final class Validate {

    private Validate() {
    }

    /** @return {@code null} if {@code specFile} loads successfully, or a one-line error message if it doesn't. */
    static String check(File specFile) {
        try {
            SpecLoader.load(specFile);
            return null;
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.toString();
        }
    }
}
