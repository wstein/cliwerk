package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.MutuallyExclusiveArgsException;
import picocli.CommandLine.ParseResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@code group} declares a picocli {@link ArgGroupSpec} (mutually exclusive or cooperative
 * options/positionals, with an optional multiplicity and heading) inside a command body.
 */
public class CommandSpecDslArgGroupTest {

    @Test
    public void exclusiveGroupOptionsAreVisibleOnTheCommand() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group exclusive {\n" +
                "    option --json : boolean\n" +
                "    option --xml : boolean\n" +
                "  }\n" +
                "}");

        assertEquals(2, spec.options().size());
        assertTrue(spec.findOption("--json") != null);
        assertTrue(spec.findOption("--xml") != null);
        assertEquals(1, spec.argGroups().size());
        ArgGroupSpec group = spec.argGroups().get(0);
        assertTrue(group.exclusive());
    }

    @Test
    public void exclusiveGroupRejectsBothOptionsTogether() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group exclusive {\n" +
                "    option --json : boolean\n" +
                "    option --xml : boolean\n" +
                "  }\n" +
                "}");
        CommandLine cmd = new CommandLine(spec);

        try {
            cmd.parseArgs("--json", "--xml");
            fail("expected MutuallyExclusiveArgsException");
        } catch (MutuallyExclusiveArgsException expected) {
            // ok
        }
    }

    @Test
    public void exclusiveGroupAcceptsEitherOptionAlone() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group exclusive {\n" +
                "    option --json : boolean\n" +
                "    option --xml : boolean\n" +
                "  }\n" +
                "}");
        CommandLine cmd = new CommandLine(spec);

        ParseResult result = cmd.parseArgs("--json");
        assertTrue(result.matchedOptionValue("--json", Boolean.FALSE));
    }

    @Test
    public void cooperativeGroupAcceptsBothOptionsTogether() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group cooperative {\n" +
                "    option --user : String\n" +
                "    option --password : String\n" +
                "  }\n" +
                "}");
        CommandLine cmd = new CommandLine(spec);

        ParseResult result = cmd.parseArgs("--user", "alice", "--password", "secret");

        assertEquals("alice", result.matchedOptionValue("--user", (String) null));
        assertEquals("secret", result.matchedOptionValue("--password", (String) null));
    }

    @Test
    public void multiplicityMakesTheGroupRequired() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group exclusive multiplicity=1 {\n" +
                "    option --json : boolean\n" +
                "    option --xml : boolean\n" +
                "  }\n" +
                "}");
        CommandLine cmd = new CommandLine(spec);

        try {
            cmd.parseArgs();
            fail("expected a missing-parameter-style exception for the required group");
        } catch (CommandLine.ParameterException expected) {
            // ok -- exact exception type depends on picocli's group validation message
        }
    }

    @Test
    public void groupAcceptsAHeadingString() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group exclusive \"Output format\" {\n" +
                "    option --json : boolean\n" +
                "  }\n" +
                "}");

        assertEquals("Output format", spec.argGroups().get(0).heading());
    }

    @Test
    public void groupAcceptsAPositionalParam() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group cooperative {\n" +
                "    positional files : File[] arity=0..*\n" +
                "  }\n" +
                "}");

        assertEquals(1, spec.positionalParameters().size());
    }

    @Test
    public void groupsCanBeNested() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group cooperative {\n" +
                "    option --a : boolean\n" +
                "    group exclusive {\n" +
                "      option --b : boolean\n" +
                "      option --c : boolean\n" +
                "    }\n" +
                "  }\n" +
                "}");

        assertEquals(1, spec.argGroups().size());
        ArgGroupSpec outer = spec.argGroups().get(0);
        assertEquals(1, outer.subgroups().size());
        assertEquals(3, spec.options().size());
    }

    @Test
    public void groupCanReferenceDefinitions() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --json : boolean\n" +
                "}\n" +
                "command flix {\n" +
                "  group exclusive {\n" +
                "    option --json\n" +
                "    option --xml : boolean\n" +
                "  }\n" +
                "}");

        assertTrue(spec.findOption("--json") != null);
    }

    @Test
    public void rejectsUnknownGroupKind() {
        try {
            CommandSpecDsl.parse("command flix { group bogus { option --a : boolean } }");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            // ok
        }
    }
}
