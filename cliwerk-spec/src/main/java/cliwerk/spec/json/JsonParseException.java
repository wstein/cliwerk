package cliwerk.spec.json;

/** Thrown when {@link Json#parse(String)} encounters malformed JSON text. */
public class JsonParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JsonParseException(String message) {
        super(message);
    }
}
