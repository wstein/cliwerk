package cliwerk.spec.tool;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SpecLoaderTest {

    private static File tempFile(String suffix, String content) throws IOException {
        File file = File.createTempFile("spec-loader-test", suffix);
        file.deleteOnExit();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    public void loadsAPicocliExtensionFileWithTheDslParser() throws IOException {
        File file = tempFile(".cliwerk", "command demo \"A demo.\" {}");

        CommandSpec spec = SpecLoader.load(file);

        assertEquals("demo", spec.name());
    }

    @Test
    public void loadsADslExtensionFileWithTheDslParser() throws IOException {
        File file = tempFile(".dsl", "command demo \"A demo.\" {}");

        CommandSpec spec = SpecLoader.load(file);

        assertEquals("demo", spec.name());
    }

    @Test
    public void loadsAJsonExtensionFileWithTheJsonReader() throws IOException {
        File file = tempFile(".json", "{ \"name\": \"demo\" }");

        CommandSpec spec = SpecLoader.load(file);

        assertEquals("demo", spec.name());
    }

    @Test
    public void rejectsAnUnrecognizedExtension() throws IOException {
        File file = tempFile(".txt", "irrelevant");

        try {
            SpecLoader.load(file);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains(".txt") || expected.getMessage().contains(file.getName()));
        }
    }
}
