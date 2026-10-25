package cliwerk.spec.tool;

import org.junit.Test;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import cliwerk.spec.CommandSpecDsl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreviewTest {

    private static String render(CommandSpec spec) throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);
        Preview.render(spec, writer, Ansi.OFF);
        writer.flush();
        return out.toString("UTF-8");
    }

    private static String render(CommandSpec spec, List<String> sections, String triggerOption) throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);
        Preview.render(spec, writer, Ansi.OFF, sections, triggerOption);
        writer.flush();
        return out.toString("UTF-8");
    }

    private static String render(CommandSpec spec, List<String> sections, String triggerOption, PreviewCommand.Format format)
            throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);
        Preview.render(spec, writer, Ansi.OFF, sections, triggerOption, format);
        writer.flush();
        return out.toString("UTF-8");
    }

    @Test
    public void printsTheRootCommandsUsageHelp() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo \"A demo tool.\" {\n" +
                "  option --verbose : boolean \"be verbose.\"\n" +
                "}");

        String printed = render(spec);

        assertTrue(printed.contains("A demo tool."));
        assertTrue(printed.contains("--verbose"));
    }

    @Test
    public void recursivelyPrintsEverySubcommandsUsageHelp() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo \"A demo tool.\" {\n" +
                "  command build \"Builds the thing.\" {\n" +
                "    option --release : boolean \"optimize for release.\"\n" +
                "  }\n" +
                "  command run \"Runs the thing.\" {}\n" +
                "}");

        String printed = render(spec);

        assertTrue(printed.contains("Builds the thing."));
        assertTrue(printed.contains("--release"));
        assertTrue(printed.contains("Runs the thing."));
    }

    @Test
    public void separatesEachCommandsUsageWithABlankLine() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  command build {}\n" +
                "}");

        String printed = render(spec);

        // Normalize CRLF to LF first: picocli's usage() writes platform line endings, which on
        // Windows means a "blank line" is "\r\n\r\n", not the literal "\n\n" this otherwise checks.
        assertTrue(printed.replace("\r\n", "\n").contains("\n\n"));
    }

    /**
     * "leaf command." legitimately appears twice: once as "b"'s one-line summary in "a"'s own
     * subcommand list, and once as "b"'s own full description -- that's correct picocli behavior,
     * not a duplicate. What must NOT be duplicated is "b"'s own dedicated usage block, which only
     * {@link Preview}'s alias-deduplication (via identity, not text) could otherwise double-print.
     */
    @Test
    public void deeplyNestedSubcommandsGetExactlyOneOwnUsageBlockEach() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  command a {\n" +
                "    command b \"leaf command.\" {}\n" +
                "  }\n" +
                "}");

        String printed = render(spec);

        int occurrences = printed.split("Usage: demo a b", -1).length - 1;
        assertEquals(1, occurrences);
    }

    @Test
    public void sectionOptionRendersTaggedSectionViaAutoDiscoveredTrigger() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  option --Xhelp : boolean \"shows experimental options.\" helpSection=\"experimental\"\n" +
                "  group cooperative helpSection=\"experimental\" \"Experimental Options:%n\" {\n" +
                "    option --Xalpha : boolean \"an experimental flag.\"\n" +
                "  }\n" +
                "}");

        String withoutSection = render(spec);
        assertFalse("standard usage should hide on-demand experimental content", withoutSection.contains("--Xalpha"));

        String withSection = render(spec, Collections.singletonList("experimental"), null);
        assertTrue("--section should append the experimental content", withSection.contains("--Xalpha"));
        assertTrue(withSection.contains("Experimental Options:"));
    }

    @Test
    public void sectionOptionIsIgnoredForCommandsThatDoNotDeclareIt() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  option --verbose : boolean \"be verbose.\"\n" +
                "}");

        String printed = render(spec, Collections.singletonList("experimental"), null);

        assertTrue(printed.contains("--verbose"));
        assertFalse(printed.contains("Experimental"));
    }

    @Test
    public void sectionWithoutUsageHelpTriggerFallsBackToDirectRendering() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  group cooperative helpSection=\"experimental\" \"Experimental Options:%n\" {\n" +
                "    option --Xalpha : boolean \"an experimental flag.\"\n" +
                "  }\n" +
                "}");

        String printed = render(spec, Collections.singletonList("experimental"), null);

        assertTrue("no usageHelp trigger declared for this section; content should still render "
                + "via the printHelpSection fallback", printed.contains("--Xalpha"));
    }

    @Test
    public void triggerOptionOverridesAutomaticDiscoveryAndIsPassedVerbatim() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  option --Xhelp : boolean \"shows experimental options.\" helpSection=\"experimental\"\n" +
                "  group cooperative helpSection=\"experimental\" \"Experimental Options:%n\" {\n" +
                "    option --Xalpha : boolean \"an experimental flag.\"\n" +
                "  }\n" +
                "}");

        String auto = render(spec, Collections.singletonList("experimental"), null);
        assertTrue(auto.contains("--Xalpha"));

        // A bogus override is passed to execute() verbatim instead of the auto-discovered
        // "--Xhelp", so picocli reports an unmatched-argument error on stderr (not on the
        // PrintWriter under test) and the section content is not rendered here.
        String overridden = render(spec, Collections.singletonList("experimental"), "--does-not-exist");
        assertFalse(overridden.contains("--Xalpha"));
    }

    @Test
    public void markdownFormatWrapsEachCommandInAFencedBlockWithAHelpPrompt() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo \"A demo tool.\" {\n" +
                "  option --verbose : boolean \"be verbose.\"\n" +
                "  command build \"Builds the thing.\" {}\n" +
                "}");

        // Normalize CRLF to LF: the fences, prompt lines and usage() output are written with
        // println / %n, i.e. platform line endings ("\r\n" on Windows), not the literal "\n" checked here.
        String printed = render(spec, Collections.<String>emptyList(), null, PreviewCommand.Format.markdown)
                .replace("\r\n", "\n");

        assertTrue("root command's block should be prefixed with its qualified name",
                printed.contains("```\n$ demo --help\n"));
        assertTrue("subcommand's block should be prefixed with its own qualified name",
                printed.contains("```\n$ demo build --help\n"));
        assertTrue(printed.contains("--verbose"));
        assertTrue(printed.contains("Builds the thing."));

        int fenceCount = printed.split("```", -1).length - 1;
        assertEquals("one opening and one closing fence per command", 4, fenceCount);
    }

    @Test
    public void markdownFormatKeepsSectionContentInsideTheSameFencedBlock() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  option --Xhelp : boolean \"shows experimental options.\" helpSection=\"experimental\"\n" +
                "  group cooperative helpSection=\"experimental\" \"Experimental Options:%n\" {\n" +
                "    option --Xalpha : boolean \"an experimental flag.\"\n" +
                "  }\n" +
                "}");

        String printed = render(spec, Collections.singletonList("experimental"), null, PreviewCommand.Format.markdown);

        int fenceCount = printed.split("```", -1).length - 1;
        assertEquals("standard usage and the appended section share a single fenced block", 2, fenceCount);
        assertTrue(printed.contains("--Xalpha"));
    }
}
