package cliwerk.spec.tool;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ValidateTest {

    private static File tempFile(String suffix, String content) throws IOException {
        File file = File.createTempFile("validate-test", suffix);
        file.deleteOnExit();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    public void returnsNullForAValidSpec() throws IOException {
        File file = tempFile(".cliwerk", "command demo \"A demo.\" {}");

        assertNull(Validate.check(file));
    }

    @Test
    public void returnsAMessageForAMalformedSpec() throws IOException {
        File file = tempFile(".cliwerk", "command demo { option --nope }");

        String message = Validate.check(file);

        assertTrue(message != null && message.contains("--nope"));
    }

    @Test
    public void returnsAMessageForAnUnrecognizedExtension() throws IOException {
        File file = tempFile(".txt", "irrelevant");

        String message = Validate.check(file);

        assertTrue(message != null);
    }
}
