package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A {@code hidden} group is NOT rendered as a real picocli {@link ArgGroupSpec}: verified
 * empirically that a group whose every member is hidden still leaves visible artifacts in usage
 * help -- an orphaned heading line if one is set, and, regardless of heading, a stray empty "[]"
 * in the synopsis line (picocli renders a bracket for the group itself even when nothing inside
 * it is visible). The only fully clean fix is to never create the group in the first place:
 * {@code hidden} flattens its members into ordinary, individually-hidden options/positionals
 * added directly to the enclosing command (or parent group, if nested).
 */
public class CommandSpecDslHiddenGroupTest {

    // PrintStream(OutputStream, boolean, Charset) and ByteArrayOutputStream#toString(Charset) are
    // Java 10+; this module targets Java 8, so the String-encoding-name overloads are used instead.
    private static String usageOf(CommandSpec spec) throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CommandLine(spec).usage(new PrintStream(out, true, "UTF-8"), CommandLine.Help.Ansi.OFF);
        return out.toString("UTF-8");
    }

    @Test
    public void hiddenGroupCreatesNoRealArgGroup() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group cooperative hidden \"The following options are experimental:\" {\n" +
                "    option --Xfoo : boolean\n" +
                "    option --Xbar : boolean\n" +
                "  }\n" +
                "}");

        assertTrue(spec.argGroups().isEmpty());
        assertTrue(spec.findOption("--Xfoo").hidden());
        assertTrue(spec.findOption("--Xbar").hidden());
        assertEquals(2, spec.options().size());
    }

    @Test
    public void hiddenGroupOptionsStillParseNormally() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group cooperative hidden {\n" +
                "    option --Xfoo : boolean\n" +
                "  }\n" +
                "}");
        CommandLine cmd = new CommandLine(spec);

        CommandLine.ParseResult result = cmd.parseArgs("--Xfoo");
        assertTrue(result.matchedOptionValue("--Xfoo", Boolean.FALSE));
    }

    @Test
    public void hiddenGroupProducesNoHeadingAndNoStrayBracketInUsage() throws UnsupportedEncodingException {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option --normal : boolean\n" +
                "  group cooperative hidden \"The following options are experimental:\" {\n" +
                "    option --Xfoo : boolean\n" +
                "  }\n" +
                "}");

        String usage = usageOf(spec);
        assertFalse(usage.contains("experimental"));
        assertFalse(usage.contains("[]"));
    }

    @Test
    public void nonHiddenGroupStillCreatesARealArgGroupWithTheHeading() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group cooperative \"Output format\" {\n" +
                "    option --json : boolean\n" +
                "  }\n" +
                "}");

        assertEquals(1, spec.argGroups().size());
        assertEquals("Output format", spec.argGroups().get(0).heading());
        assertFalse(spec.findOption("--json").hidden());
    }

    @Test
    public void hiddenSubgroupFlattensIntoTheVisibleParentGroup() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  group cooperative \"Output\" {\n" +
                "    option --json : boolean\n" +
                "    group exclusive hidden {\n" +
                "      option --Xa : boolean\n" +
                "      option --Xb : boolean\n" +
                "    }\n" +
                "  }\n" +
                "}");

        assertEquals(1, spec.argGroups().size());
        ArgGroupSpec outer = spec.argGroups().get(0);
        assertTrue(outer.subgroups().isEmpty());
        assertEquals(3, outer.args().size());
        assertTrue(spec.findOption("--Xa").hidden());
        assertTrue(spec.findOption("--Xb").hidden());
        assertFalse(spec.findOption("--json").hidden());
    }
}
