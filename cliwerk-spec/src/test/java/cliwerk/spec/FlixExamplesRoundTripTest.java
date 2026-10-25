package cliwerk.spec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Parameterized round-trip fidelity test exercising the real-world Flix .cliwerk spec file
 * in {@code cliwerk-spec/examples/}.
 *
 * Validates that:
 * 1. The .cliwerk DSL parses cleanly.
 * 2. Emitting DSL via {@link CommandSpecDsl#write} and re-parsing yields an equivalent spec.
 * 3. Emitting JSON via {@link CommandSpecJson#write} and re-reading yields an equivalent spec.
 */
@RunWith(Parameterized.class)
public class FlixExamplesRoundTripTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<String> specFiles() {
        return Collections.singletonList(
                "flix-0.60.0.cliwerk"
        );
    }

    private final String fileName;

    public FlixExamplesRoundTripTest(String fileName) {
        this.fileName = fileName;
    }

    @Test
    public void roundTripsDslFaithfully() throws IOException {
        File file = new File("examples/" + fileName);
        String dsl = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        CommandSpec original = CommandSpecDsl.parse(dsl);
        assertNotNull(fileName + " failed to parse", original);

        String emittedDsl = CommandSpecDsl.write(original);
        CommandSpec roundTripped = CommandSpecDsl.parse(emittedDsl);

        assertEquals(fileName + " name mismatch", original.name(), roundTripped.name());
        assertEquals(fileName + " subcommands mismatch", original.subcommands().keySet(), roundTripped.subcommands().keySet());
        assertEquals(fileName + " mixinStandardHelpOptions mismatch", original.mixinStandardHelpOptions(), roundTripped.mixinStandardHelpOptions());
    }

    @Test
    public void roundTripsJsonFaithfully() throws IOException {
        File file = new File("examples/" + fileName);
        String dsl = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        CommandSpec original = CommandSpecDsl.parse(dsl);
        assertNotNull(fileName + " failed to parse", original);

        String json = CommandSpecJson.write(original);
        CommandSpec roundTripped = CommandSpecJson.read(json);

        assertEquals(fileName + " JSON name mismatch", original.name(), roundTripped.name());
        assertEquals(fileName + " JSON subcommands mismatch", original.subcommands().keySet(), roundTripped.subcommands().keySet());
        assertEquals(fileName + " JSON mixinStandardHelpOptions mismatch", original.mixinStandardHelpOptions(), roundTripped.mixinStandardHelpOptions());
    }

    @Test
    public void companionJsonMatchesDslSpec() throws IOException {
        if ("flix-0.60.0.cliwerk".equals(fileName) || "flix-0.76.2.cliwerk".equals(fileName)) {
            File dslFile = new File("examples/" + fileName);
            String dsl = new String(Files.readAllBytes(dslFile.toPath()), StandardCharsets.UTF_8);
            CommandSpec fromDsl = CommandSpecDsl.parse(dsl);

            String jsonFileName = fileName.replace(".cliwerk", ".json");
            File jsonFile = new File("examples/" + jsonFileName);
            String json = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
            CommandSpec fromJson = CommandSpecJson.read(json);

            assertEquals(fileName + " companion JSON name mismatch", fromDsl.name(), fromJson.name());
            assertEquals(fileName + " companion JSON subcommands mismatch", fromDsl.subcommands().keySet(), fromJson.subcommands().keySet());
            assertEquals(fileName + " companion JSON mixinStandardHelpOptions mismatch", fromDsl.mixinStandardHelpOptions(), fromJson.mixinStandardHelpOptions());
        }
    }
}
