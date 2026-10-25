package cliwerk.spec;

import org.junit.Test;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@code examples/flix-0.60.0.cliwerk}'s {@code --Xhelp}:
 * the declarative {@code helpSection="experimental"} group and trigger option natively render
 * experimental options on demand, exclude them from standard {@code --help}, and keep them
 * available for shell autocompletion.
 */
public class FlixExperimentalHelpTest {

    private static PrintWriter utf8Writer(ByteArrayOutputStream out) throws UnsupportedEncodingException {
        return new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);
    }

    @Test
    public void xhelpNativelyPrintsOnlyTheExperimentalOptionsForThatCommand() throws IOException {
        File file = new File("examples/flix-0.60.0.cliwerk");
        String dsl = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        CommandSpec flix = CommandSpecDsl.parse(dsl);
        CommandLine cmd = new CommandLine(flix);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cmd.setOut(utf8Writer(out));

        int exitCode = cmd.execute("check", "--Xhelp");
        assertEquals(0, exitCode);

        String printed = out.toString("UTF-8");
        assertTrue("contains experimental group heading", printed.contains("The following options are experimental"));
        assertTrue(printed.contains("--Xfuzzer"));
        assertTrue(printed.contains("enables compiler fuzzing"));
        assertTrue(printed.contains("--Xiterations"));
        assertFalse("a non-experimental option must not be printed", printed.contains("--explain"));
        assertFalse("--Xhelp itself is not an experimental flag, so it must not be in the experimental group", printed.contains("--Xhelp"));
        assertFalse("standard synopsis must not be in on-demand help", printed.contains("Usage:"));
    }

    @Test
    public void standardHelpExcludesExperimentalOptions() throws IOException {
        File file = new File("examples/flix-0.60.0.cliwerk");
        String dsl = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        CommandSpec flix = CommandSpecDsl.parse(dsl);
        CommandLine cmd = new CommandLine(flix);

        CommandLine checkCmd = cmd.getSubcommands().get("check");
        String printed = checkCmd.getUsageMessage();

        assertTrue(printed.contains("--explain"));
        assertTrue("shows --Xhelp trigger option in standard options", printed.contains("--Xhelp"));

        assertFalse("must not contain experimental heading", printed.contains("The following options are experimental"));
        assertFalse("must not contain --Xfuzzer", printed.contains("--Xfuzzer"));
        assertFalse("must not contain --Xiterations", printed.contains("--Xiterations"));

        // Top-level flix also executes --help cleanly
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cmd.setOut(utf8Writer(out));
        int exitCode = cmd.execute("--help");
        assertEquals(0, exitCode);
        assertTrue(out.toString("UTF-8").contains("The Flix Programming Language"));
    }

    @Test
    public void shellCompletionIncludesExperimentalOptions() throws IOException {
        File file = new File("examples/flix-0.60.0.cliwerk");
        String dsl = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        CommandSpec flix = CommandSpecDsl.parse(dsl);
        CommandLine cmd = new CommandLine(flix);

        String fish = AutoComplete.fish("flix", cmd);
        assertTrue("fish completion must include --Xbenchmark-code-size", fish.contains("'Xbenchmark-code-size'"));
        assertTrue("fish completion must include --Xlib", fish.contains("'Xlib'"));
        assertTrue("fish completion must include standard --threads", fish.contains("'threads'"));

        String bash = AutoComplete.bash("flix", cmd);
        assertTrue("bash completion must include --Xbenchmark-code-size", bash.contains("--Xbenchmark-code-size"));
        assertTrue("bash completion must include --Xlib", bash.contains("--Xlib"));
        assertTrue("bash completion must include standard --threads", bash.contains("--threads"));
    }

    @Test
    public void commandsWithoutXflagsHaveNoExperimentalOptionsToPrint() throws IOException {
        File file = new File("examples/flix-0.60.0.cliwerk");
        String dsl = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        CommandSpec flix = CommandSpecDsl.parse(dsl);
        CommandLine cmd = new CommandLine(flix);

        CommandLine docCmd = cmd.getSubcommands().get("doc");
        String rendered = docCmd.getHelp().renderHelpSection("experimental");
        assertTrue(rendered.isEmpty());
    }
}
