package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A top-level {@code definitions { ... }} block, parsed before the {@code command} block, lets
 * an option or positional param be described once and referenced by name (no {@code :}) from
 * multiple commands instead of being repeated verbatim -- the DSL equivalent of JSON's
 * {@code "definitions"} dictionary in {@link CommandSpecJsonDefinitionsTest}.
 */
public class CommandSpecDslDefinitionsTest {

    @Test
    public void resolvesAnOptionReferenceAgainstTheDefinitionsBlock() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --explain : boolean \"provides suggestions\"\n" +
                "}\n" +
                "command flix {\n" +
                "  command check {\n" +
                "    option --explain\n" + // reference: no ':'
                "  }\n" +
                "}");

        OptionSpec explain = spec.subcommands().get("check").getCommandSpec().findOption("--explain");
        assertEquals(boolean.class, explain.type());
        assertEquals("provides suggestions", explain.description()[0]);
    }

    @Test
    public void resolvesAPositionalReferenceAgainstTheDefinitionsBlock() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  positional files : File \"input files\" arity=0..*\n" +
                "}\n" +
                "command flix {\n" +
                "  command check {\n" +
                "    positional files\n" + // reference: no ':'
                "  }\n" +
                "  command build {\n" +
                "    positional files\n" +
                "  }\n" +
                "}");

        PositionalParamSpec checkFiles = spec.subcommands().get("check").getCommandSpec().positionalParameters().get(0);
        PositionalParamSpec buildFiles = spec.subcommands().get("build").getCommandSpec().positionalParameters().get(0);
        assertEquals("<files>", checkFiles.paramLabel());
        assertEquals(File.class, checkFiles.type());
        assertEquals("0..*", checkFiles.arity().toString());
        assertEquals(checkFiles.paramLabel(), buildFiles.paramLabel());
    }

    @Test
    public void twoCommandsReferencingTheSameDefinitionGetIndependentInstances() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --json : boolean\n" +
                "}\n" +
                "command flix {\n" +
                "  command check { option --json }\n" +
                "  command build { option --json }\n" +
                "}");

        OptionSpec checkJson = spec.subcommands().get("check").getCommandSpec().findOption("--json");
        OptionSpec buildJson = spec.subcommands().get("build").getCommandSpec().findOption("--json");
        assertNotSame(checkJson, buildJson);
    }

    @Test
    public void definitionAndReferenceCanBeMixedInTheSameCommand() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --json : boolean\n" +
                "}\n" +
                "command flix {\n" +
                "  command check {\n" +
                "    option --json\n" +
                "    option --threads : int\n" +
                "  }\n" +
                "}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(2, check.options().size());
        assertTrue(check.findOption("--json") != null);
        assertTrue(check.findOption("--threads") != null);
    }

    @Test
    public void rejectsReferenceToUndefinedOption() {
        try {
            CommandSpecDsl.parse("command flix { option --nope }");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            assertTrue(expected.getMessage().contains("--nope"));
        }
    }

    @Test
    public void rejectsMultiNameOptionReference() {
        try {
            CommandSpecDsl.parse(
                    "definitions { option -v, --verbose : boolean }\n" +
                    "command flix { option -v, --verbose }");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            // ok - a reference must name exactly one definition
        }
    }

    @Test
    public void parsesWithoutADefinitionsBlockAsBefore() {
        CommandSpec spec = CommandSpecDsl.parse("command flix { option --verbose : boolean }");
        assertTrue(spec.findOption("--verbose") != null);
    }
}
