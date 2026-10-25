package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;
import cliwerk.spec.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the DSL and JSON reader/writer against the checked-in {@code fixtures/flix.*}
 * example: a single realistic spec that uses every field the format supports (multi-name
 * options, a default value, a required option, a positional with arity, and nested
 * subcommands). These fixtures also back the module README and the JSON Schema.
 */
public class CommandSpecFixturesTest {

    private static String readFixture(String name) {
        InputStream in = CommandSpecFixturesTest.class.getResourceAsStream("fixtures/" + name);
        if (in == null) {
            throw new IllegalStateException("Fixture not found on classpath: fixtures/" + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fixture: fixtures/" + name, e);
        } finally {
            try { in.close(); } catch (IOException ignored) { /* best effort */ }
        }
    }

    /**
     * Compares parsed JSON trees rather than raw text, and ignores an optional top-level
     * {@code "$schema"} hint (the conventional editor-validation key some tools add to JSON
     * files on save) since {@link CommandSpecJson#write} never emits one and it carries no
     * meaning for {@link CommandSpecJson#read}.
     */
    @SuppressWarnings("unchecked")
    private static Object withoutSchemaHint(String json) {
        Object parsed = Json.parse(json);
        if (parsed instanceof Map) {
            ((Map<String, Object>) parsed).remove("$schema");
        }
        return parsed;
    }

    @Test
    public void dslFixtureCompilesToTheJsonFixture() {
        CommandSpec spec = CommandSpecDsl.parse(readFixture("flix.cliwerk"));

        String actualJson = CommandSpecJson.write(spec);

        assertEquals(withoutSchemaHint(readFixture("flix.json")), withoutSchemaHint(actualJson));
    }

    @Test
    public void jsonFixtureReadsBackToAnEquivalentCommandSpec() {
        CommandSpec fromJson = CommandSpecJson.read(readFixture("flix.json"));
        CommandSpec fromDsl = CommandSpecDsl.parse(readFixture("flix.cliwerk"));

        assertEquals(CommandSpecJson.write(fromDsl), CommandSpecJson.write(fromJson));
    }

    @Test
    public void fixtureSpecActuallyParsesRealisticArguments() {
        CommandSpec spec = CommandSpecJson.read(readFixture("flix.json"));
        CommandLine cmd = new CommandLine(spec);

        ParseResult result = cmd.parseArgs("--target", "jvm", "build", "--release", "Main.flix");

        assertEquals("jvm", result.matchedOptionValue("--target", (String) null));
        assertTrue(result.hasSubcommand());
        ParseResult build = result.subcommand();
        assertTrue(build.matchedOptionValue("--release", Boolean.FALSE));
        assertEquals(1, build.matchedPositionals().size());
    }
}
