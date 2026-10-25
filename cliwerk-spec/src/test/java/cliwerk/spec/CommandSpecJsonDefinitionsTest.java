package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A document-root {@code "definitions"} dictionary lets an option or positional param be
 * described once and referenced by name from multiple commands, instead of being repeated
 * verbatim -- the JSON equivalent of the DSL's {@code definitions { ... }} block. Only
 * {@link CommandSpecJson#read} understands this shorthand; {@link CommandSpecJson#write} always
 * emits fully-inlined objects (no attempt to re-fold duplicates back into a dictionary on write).
 */
public class CommandSpecJsonDefinitionsTest {

    @Test
    public void resolvesAStringOptionReferenceAgainstTheDefinitionsDictionary() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": { \"options\": {" +
                "\"--explain\": { \"names\": [\"--explain\"], \"type\": \"boolean\", \"description\": [\"provides suggestions\"] }" +
                "} }, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"options\": [\"--explain\"] }" +
                "]}");

        OptionSpec explain = spec.subcommands().get("check").getCommandSpec().findOption("--explain");
        assertEquals(boolean.class, explain.type());
        assertEquals("provides suggestions", explain.description()[0]);
    }

    @Test
    public void resolvesAStringPositionalReferenceAgainstTheDefinitionsDictionary() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": { \"positionalParams\": {" +
                "\"files\": { \"paramLabel\": \"<files>\", \"type\": \"File\", \"arity\": \"0..*\" }" +
                "} }, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"positionalParams\": [\"files\"] }," +
                "{ \"name\": \"build\", \"positionalParams\": [\"files\"] }" +
                "]}");

        PositionalParamSpec checkFiles = spec.subcommands().get("check").getCommandSpec().positionalParameters().get(0);
        PositionalParamSpec buildFiles = spec.subcommands().get("build").getCommandSpec().positionalParameters().get(0);
        assertEquals("<files>", checkFiles.paramLabel());
        assertEquals(File.class, checkFiles.type());
        assertEquals("0..*", checkFiles.arity().toString());
        assertEquals(checkFiles.paramLabel(), buildFiles.paramLabel());
    }

    @Test
    public void twoCommandsReferencingTheSameDefinitionGetIndependentInstances() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": { \"options\": {" +
                "\"--json\": { \"names\": [\"--json\"], \"type\": \"boolean\" }" +
                "} }, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"options\": [\"--json\"] }," +
                "{ \"name\": \"build\", \"options\": [\"--json\"] }" +
                "]}");

        OptionSpec checkJson = spec.subcommands().get("check").getCommandSpec().findOption("--json");
        OptionSpec buildJson = spec.subcommands().get("build").getCommandSpec().findOption("--json");
        assertNotSame(checkJson, buildJson);

        // independence actually matters: matching one shouldn't affect the other's matched state
        assertFalse(checkJson.equals(buildJson) && checkJson == buildJson);
    }

    @Test
    public void inlineDefinitionsAndReferencesCanBeMixedInTheSameArray() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": { \"options\": {" +
                "\"--json\": { \"names\": [\"--json\"], \"type\": \"boolean\" }" +
                "} }, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"options\": [" +
                "  \"--json\"," +
                "  { \"names\": [\"--threads\"], \"type\": \"int\" }" +
                "] }" +
                "]}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(2, check.options().size());
        assertTrue(check.findOption("--json") != null);
        assertTrue(check.findOption("--threads") != null);
    }

    @Test
    public void rejectsReferenceToUndefinedOption() {
        try {
            CommandSpecJson.read("{ \"name\": \"flix\", \"subcommands\": [" +
                    "{ \"name\": \"check\", \"options\": [\"--nope\"] }" +
                    "]}");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("--nope"));
        }
    }

    @Test
    public void rejectsReferenceToUndefinedPositional() {
        try {
            CommandSpecJson.read("{ \"name\": \"flix\", \"subcommands\": [" +
                    "{ \"name\": \"check\", \"positionalParams\": [\"nope\"] }" +
                    "]}");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("nope"));
        }
    }

    @Test
    public void writeAlwaysInlinesEvenWhenReadFromADefinitionsReference() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": { \"options\": {" +
                "\"--json\": { \"names\": [\"--json\"], \"type\": \"boolean\", \"description\": [\"enables json output.\"] }" +
                "} }, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"options\": [\"--json\"] }" +
                "]}");

        String written = CommandSpecJson.write(spec);

        assertFalse(written.contains("\"definitions\""));
        assertTrue(written.contains("\"enables json output.\""));
    }

    @Test
    public void rejectsADefinitionsBlockNestedInASubcommand() {
        try {
            CommandSpecJson.read("{ \"name\": \"flix\", \"subcommands\": [" +
                    "{ \"name\": \"check\", \"definitions\": { \"options\": {" +
                    "\"--explain\": { \"names\": [\"--explain\"], \"type\": \"boolean\" }" +
                    "} }, \"options\": [\"--explain\"] }" +
                    "]}");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("\"definitions\""));
            assertTrue(message, message.contains("check"));
            assertTrue(message, message.contains("document root"));
        }
    }

    @Test
    public void rejectsADefinitionsBlockNestedInADeeplyNestedSubcommand() {
        try {
            CommandSpecJson.read("{ \"name\": \"flix\", \"subcommands\": [" +
                    "{ \"name\": \"check\", \"subcommands\": [" +
                    "{ \"name\": \"deep\", \"definitions\": { \"options\": {} } }" +
                    "] }]}");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("deep"));
        }
    }
}
