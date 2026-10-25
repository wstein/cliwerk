package cliwerk.spec;

import org.junit.Test;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.HelpSectionSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HelpSectionTest {

    private static final String TEST_DSL =
            "command testapp {\n" +
            "  option -h, --help : boolean \"display this help and exit\" usageHelp\n" +
            "  option --normal : String \"a normal option\"\n" +
            "  option --Xhelp : boolean \"shows experimental options.\" helpSection=\"experimental\"\n" +
            "  group cooperative helpSection=\"experimental\" \"The following options are experimental:%n\" {\n" +
            "    option --Xalpha : boolean \"experimental alpha feature\"\n" +
            "    option --Xbeta : String \"experimental beta setting\"\n" +
            "  }\n" +
            "}\n";

    @Test
    public void testDslParseGroupAndOptionWithHelpSection() {
        CommandSpec spec = CommandSpecDsl.parse(TEST_DSL);

        OptionSpec xhelp = spec.findOption("--Xhelp");
        assertNotNull(xhelp);
        assertTrue("helpSection option implies usageHelp", xhelp.usageHelp());
        assertEquals("experimental", Help.getHelpSection(xhelp));

        assertEquals(1, spec.argGroups().size());
        ArgGroupSpec group = spec.argGroups().get(0);
        assertEquals("experimental", Help.getHelpSection(group));

        OptionSpec xalpha = spec.findOption("--Xalpha");
        assertNotNull(xalpha);
        assertFalse("options in tagged group must NOT be hidden", xalpha.hidden());

        OptionSpec xbeta = spec.findOption("--Xbeta");
        assertNotNull(xbeta);
        assertFalse("options in tagged group must NOT be hidden", xbeta.hidden());
    }

    @Test
    public void testDslRoundTrip() {
        CommandSpec spec1 = CommandSpecDsl.parse(TEST_DSL);
        String writtenDsl = CommandSpecDsl.write(spec1);
        CommandSpec spec2 = CommandSpecDsl.parse(writtenDsl);

        OptionSpec xhelp = spec2.findOption("--Xhelp");
        assertNotNull(xhelp);
        assertTrue(xhelp.usageHelp());
        assertEquals("experimental", Help.getHelpSection(xhelp));

        assertEquals(1, spec2.argGroups().size());
        ArgGroupSpec group = spec2.argGroups().get(0);
        assertEquals("experimental", Help.getHelpSection(group));
        assertNotNull(spec2.findOption("--Xalpha"));
    }

    @Test
    public void testJsonRoundTrip() {
        CommandSpec spec1 = CommandSpecDsl.parse(TEST_DSL);
        String json = CommandSpecJson.write(spec1);
        assertTrue(json.contains("\"helpSection\": \"experimental\""));

        CommandSpec spec2 = CommandSpecJson.read(json);
        OptionSpec xhelp = spec2.findOption("--Xhelp");
        assertNotNull(xhelp);
        assertTrue(xhelp.usageHelp());
        assertEquals("experimental", Help.getHelpSection(xhelp));

        assertEquals(1, spec2.argGroups().size());
        ArgGroupSpec group = spec2.argGroups().get(0);
        assertEquals("experimental", Help.getHelpSection(group));
        assertNotNull(spec2.findOption("--Xalpha"));
    }

    @Test
    public void testCommandHelpSectionDslAndJsonRoundTrip() {
        String dsl = "command myapp {\n" +
                "  command exp-cmd helpSection=\"experimental\" \"An experimental subcommand\" {\n" +
                "    option --foo : boolean\n" +
                "  }\n" +
                "  command std-cmd \"A standard subcommand\" {\n" +
                "    option --bar : boolean\n" +
                "  }\n" +
                "}";

        CommandSpec spec1 = CommandSpecDsl.parse(dsl);
        CommandSpec expCmd = spec1.subcommands().get("exp-cmd").getCommandSpec();
        assertEquals("experimental", expCmd.helpSection());
        assertEquals("experimental", Help.getHelpSection(expCmd));

        CommandSpec stdCmd = spec1.subcommands().get("std-cmd").getCommandSpec();
        assertEquals("", stdCmd.helpSection());

        String writtenDsl = CommandSpecDsl.write(spec1);
        assertTrue(writtenDsl.contains("command exp-cmd helpSection=\"experimental\""));
        CommandSpec specFromDsl = CommandSpecDsl.parse(writtenDsl);
        assertEquals("experimental", specFromDsl.subcommands().get("exp-cmd").getCommandSpec().helpSection());

        String json = CommandSpecJson.write(spec1);
        assertTrue(json.contains("\"helpSection\": \"experimental\""));
        CommandSpec specFromJson = CommandSpecJson.read(json);
        assertEquals("experimental", specFromJson.subcommands().get("exp-cmd").getCommandSpec().helpSection());
    }

    @Test
    public void testPositionalParamHelpSectionDslAndJsonRoundTrip() {
        String dsl = "command myapp {\n" +
                "  positional exp-file : File \"An experimental file\" helpSection=\"experimental\"\n" +
                "  positional std-file : File \"A standard file\"\n" +
                "}";

        CommandSpec spec1 = CommandSpecDsl.parse(dsl);
        PositionalParamSpec expFile = spec1.positionalParameters().get(0);
        assertEquals("experimental", expFile.helpSection());
        assertEquals("experimental", Help.getHelpSection(expFile));

        PositionalParamSpec stdFile = spec1.positionalParameters().get(1);
        assertEquals("", stdFile.helpSection());

        String writtenDsl = CommandSpecDsl.write(spec1);
        assertTrue(writtenDsl.contains("helpSection=\"experimental\""));
        CommandSpec specFromDsl = CommandSpecDsl.parse(writtenDsl);
        assertEquals("experimental", specFromDsl.positionalParameters().get(0).helpSection());

        String json = CommandSpecJson.write(spec1);
        assertTrue(json.contains("\"helpSection\": \"experimental\""));
        CommandSpec specFromJson = CommandSpecJson.read(json);
        assertEquals("experimental", specFromJson.positionalParameters().get(0).helpSection());
    }

    @Test
    public void testSectionMetadataDslAndJsonRoundTrip() {
        String dsl = "command myapp {\n" +
                "  section experimental \"Flags still under development.\" heading=\"Experimental Options:%n\" emptyMessage=\"No experimental options for this command.%n\" notice=\"%nRun 'myapp --Xhelp' to inspect experimental flags.%n\"\n" +
                "  option --Xhelp : boolean \"shows experimental options.\" helpSection=\"experimental\"\n" +
                "}";

        CommandSpec spec1 = CommandSpecDsl.parse(dsl);
        HelpSectionSpec expSec = spec1.helpSectionSpec("experimental");
        assertNotNull(expSec);
        assertEquals("Experimental Options:%n", expSec.heading());
        assertEquals("No experimental options for this command.%n", expSec.emptyMessage());
        assertEquals("%nRun 'myapp --Xhelp' to inspect experimental flags.%n", expSec.notice());
        assertEquals(1, expSec.description().length);
        assertEquals("Flags still under development.", expSec.description()[0]);

        String writtenDsl = CommandSpecDsl.write(spec1);
        assertTrue(writtenDsl.contains("section experimental"));
        assertTrue(writtenDsl.contains("heading=\"Experimental Options:%n\""));
        CommandSpec specFromDsl = CommandSpecDsl.parse(writtenDsl);
        HelpSectionSpec dslSec = specFromDsl.helpSectionSpec("experimental");
        assertEquals(expSec, dslSec);

        String json = CommandSpecJson.write(spec1);
        assertTrue(json.contains("\"helpSections\""));
        assertTrue(json.contains("\"emptyMessage\": \"No experimental options for this command.%n\""));
        CommandSpec specFromJson = CommandSpecJson.read(json);
        HelpSectionSpec jsonSec = specFromJson.helpSectionSpec("experimental");
        assertEquals(expSec, jsonSec);
    }

    @Test
    public void testStandardHelpSuppressesTaggedHelpSection() {
        CommandSpec spec = CommandSpecDsl.parse(TEST_DSL);
        CommandLine cmd = new CommandLine(spec);

        String usage = cmd.getUsageMessage();
        assertTrue("standard help contains --help", usage.contains("--help"));
        assertTrue("standard help contains --normal", usage.contains("--normal"));
        assertTrue("standard help contains --Xhelp so users know it exists", usage.contains("--Xhelp"));

        assertFalse("standard help must NOT contain --Xalpha", usage.contains("--Xalpha"));
        assertFalse("standard help must NOT contain --Xbeta", usage.contains("--Xbeta"));
        assertFalse("standard help must NOT contain experimental heading",
                usage.contains("The following options are experimental"));
    }

    @Test
    public void testOnDemandHelpTriggersWhenOptionMatched() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(TEST_DSL);
        CommandLine cmd = new CommandLine(spec);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true));

        int exitCode = cmd.execute("--Xhelp");
        assertEquals(0, exitCode);

        String output = out.toString("UTF-8");
        assertTrue("must contain experimental heading", output.contains("The following options are experimental"));
        assertTrue("must contain --Xalpha", output.contains("--Xalpha"));
        assertTrue("must contain experimental alpha feature", output.contains("experimental alpha feature"));
        assertTrue("must contain --Xbeta", output.contains("--Xbeta"));

        assertFalse("must NOT contain standard Usage: synopsis", output.contains("Usage:"));
        assertFalse("must NOT contain normal options in on-demand help", output.contains("--normal"));
        assertFalse("must NOT contain --help in on-demand help", output.contains("--help"));
    }

    @Test
    public void testAutoCompleteIncludesExperimentalOptions() {
        CommandSpec spec = CommandSpecDsl.parse(TEST_DSL);
        CommandLine cmd = new CommandLine(spec);

        String fish = AutoComplete.fish("testapp", cmd);
        assertTrue("Fish completion must contain Xalpha", fish.contains("'Xalpha'"));
        assertTrue("Fish completion must contain Xbeta", fish.contains("'Xbeta'"));
        assertTrue("Fish completion must contain normal", fish.contains("'normal'"));

        String bash = AutoComplete.bash("testapp", cmd);
        assertTrue("Bash completion must contain --Xalpha", bash.contains("--Xalpha"));
        assertTrue("Bash completion must contain --Xbeta", bash.contains("--Xbeta"));
    }
}
