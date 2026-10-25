package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sanity-checks {@code examples/flix-0.60.0.cliwerk} -- a realistic, hand-curated spec for the real
 * flix CLI, kept at the module root (not under src/test/resources) so it stays easy to browse on
 * GitHub as documentation in its own right. Loaded here via a path relative to the module
 * directory, which is Gradle's default test working directory for this project.
 */
public class Flix0600ExampleTest {

    private static String readExample() throws IOException {
        File file = new File("examples/flix-0.60.0.cliwerk");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void parsesIntoTheDocumentedCommandSet() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());

        assertEquals("flix", flix.name());
        assertEquals(new HashSet<String>(Arrays.asList(
                "init", "check", "build", "build-jar", "build-fatjar", "build-pkg", "doc",
                "run", "test", "repl", "lsp", "lsp-vscode", "release", "outdated")),
                flix.subcommands().keySet());
    }

    /**
     * {@code --help}/{@code --version} are meta-options that make sense with no command at all;
     * {@code --listen} is confirmed (empirically, {@code ./flixw --listen 8099}) to start its
     * WebSocket server standalone, with no subcommand, so it belongs here too rather than under
     * any specific subcommand.
     */
    @Test
    public void topLevelOnlyHasTheOptionsThatWorkWithoutACommand() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());

        Set<String> topLevelOptionNames = new HashSet<String>();
        for (CommandLine.Model.OptionSpec option : flix.options()) { topLevelOptionNames.add(option.longestName()); }

        assertEquals(new HashSet<String>(Arrays.asList("--help", "--version", "--listen")), topLevelOptionNames);
    }

    @Test
    public void listenWorksWithNoSubcommand() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());
        CommandLine cmd = new CommandLine(flix);

        ParseResult result = cmd.parseArgs("--listen", "8099");

        assertFalse(result.hasSubcommand());
        assertEquals(8099, (int) result.matchedOptionValue("--listen", 0));
    }

    @Test
    public void runAcceptsArgsAndEntrypointButBuildPkgDoesNot() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());

        CommandSpec run = flix.subcommands().get("run").getCommandSpec();
        assertTrue(run.findOption("--args") != null);
        assertTrue(run.findOption("--entrypoint") != null);

        CommandSpec buildPkg = flix.subcommands().get("build-pkg").getCommandSpec();
        assertEquals(null, buildPkg.findOption("--args"));
        assertEquals(null, buildPkg.findOption("--entrypoint"));
    }

    @Test
    public void lspVscodeHasARequiredPortPositional() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());

        CommandSpec lspVscode = flix.subcommands().get("lsp-vscode").getCommandSpec();
        assertEquals(1, lspVscode.positionalParameters().size());
        assertEquals("<port>", lspVscode.positionalParameters().get(0).paramLabel());
        assertTrue(lspVscode.positionalParameters().get(0).required());
    }

    @Test
    public void builtCommandLineActuallyParsesARealisticInvocation() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());
        CommandLine cmd = new CommandLine(flix);

        ParseResult result = cmd.parseArgs("run", "--entrypoint", "Main.main", "--args", "hello world", "Main.flix");

        assertTrue(result.hasSubcommand());
        ParseResult run = result.subcommand();
        assertEquals("Main.main", run.matchedOptionValue("--entrypoint", (String) null));
        assertEquals("hello world", run.matchedOptionValue("--args", (String) null));
        assertEquals(1, run.matchedPositionals().size());
    }

    /**
     * Regression test for a real bug this example shipped with: the shared "files" positional
     * was declared with a scalar type ("File") at arity=0..*, which picocli rejects for a second
     * value (UnmatchedArgumentException) since a scalar field can only ever hold one match. Now
     * declared "File[]"; this proves multiple files actually parse, not just that one does.
     */
    @Test
    public void filesPositionalActuallyAcceptsMultipleValues() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());
        CommandLine cmd = new CommandLine(flix);

        ParseResult result = cmd.parseArgs("build", "A.flix", "B.flix", "C.flix");

        ParseResult build = result.subcommand();
        assertEquals(1, build.matchedPositionals().size());
        java.nio.file.Path[] files = build.matchedPositionalValue(0, new java.nio.file.Path[0]);
        assertEquals(3, files.length);
    }

    /**
     * The 14 --X* flags are hidden (via a {@code hidden} group -- per examples/README.md's
     * reasoning, a fully hidden group can't be a real picocli ArgGroupSpec without leaving
     * visible rendering artifacts) rather than shown under a visible heading, and pulled into
     * each command via a "use xflags" bundle reference instead of listed individually. A
     * plain, non-hidden --Xhelp flag is declared alongside them for a host application to wire
     * up (see FlixExperimentalHelp).
     */
    @Test
    public void experimentalFlagsAreTaggedHelpSectionAndFullyFunctional() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());
        CommandSpec check = flix.subcommands().get("check").getCommandSpec();

        assertEquals(1, check.argGroups().size());
        assertEquals("experimental", CommandLine.Help.getHelpSection(check.argGroups().get(0)));
        assertFalse(check.findOption("--Xiterations").hidden());
        assertTrue(check.findOption("--Xhelp") != null);
        assertTrue(check.findOption("--Xhelp").usageHelp());
        assertEquals("experimental", CommandLine.Help.getHelpSection(check.findOption("--Xhelp")));

        CommandLine cmd = new CommandLine(flix);
        ParseResult result = cmd.parseArgs("check", "--Xfuzzer", "--Xchaos-monkey");
        ParseResult checkResult = result.subcommand();
        assertTrue(checkResult.matchedOptionValue("--Xfuzzer", Boolean.FALSE));
        assertTrue(checkResult.matchedOptionValue("--Xchaos-monkey", Boolean.FALSE));
    }

    @Test
    public void roundTripsFlixSpec() throws IOException {
        CommandSpec flix = CommandSpecDsl.parse(readExample());
        String dsl = CommandSpecDsl.write(flix);
        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);

        assertEquals(flix.name(), roundTripped.name());
        assertEquals(flix.subcommands().keySet(), roundTripped.subcommands().keySet());
        for (String subName : flix.subcommands().keySet()) {
            CommandSpec origSub = flix.subcommands().get(subName).getCommandSpec();
            CommandSpec rtSub = roundTripped.subcommands().get(subName).getCommandSpec();
            assertEquals(subName + " option count", origSub.options().size(), rtSub.options().size());
            assertEquals(subName + " positional count", origSub.positionalParameters().size(), rtSub.positionalParameters().size());
        }

        String json = CommandSpecJson.write(flix);
        CommandSpec jsonRoundTripped = CommandSpecJson.read(json);
        assertEquals(flix.name(), jsonRoundTripped.name());
        assertEquals(flix.subcommands().keySet(), jsonRoundTripped.subcommands().keySet());
    }
}
