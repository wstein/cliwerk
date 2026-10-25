package cliwerk.spec;

/** Thrown when {@link CommandSpecDsl#parse(String)} encounters malformed DSL text. */
public class DslParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DslParseException(String message) {
        super(message);
    }
}
