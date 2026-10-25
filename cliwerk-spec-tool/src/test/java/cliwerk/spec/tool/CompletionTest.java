package cliwerk.spec.tool;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;
import cliwerk.spec.CommandSpecDsl;

import static org.junit.Assert.assertTrue;

public class CompletionTest {

    @Test
    public void generatesABashScriptMentioningEveryOptionAndSubcommand() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo \"A demo tool.\" {\n" +
                "  option --verbose : boolean \"be verbose.\"\n" +
                "  command build \"Builds the thing.\" {\n" +
                "    option --release : boolean \"optimize for release.\"\n" +
                "  }\n" +
                "}");

        String script = Completion.bashScript(spec, "demo");

        assertTrue(script.contains("#!/usr/bin/env bash"));
        assertTrue(script.contains("--verbose"));
        assertTrue(script.contains("--release"));
        assertTrue(script.contains("build"));
    }

    @Test
    public void generatesAFishScriptMentioningEveryOptionAndSubcommand() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo \"A demo tool.\" {\n" +
                "  option --verbose : boolean \"be verbose.\"\n" +
                "  command build \"Builds the thing.\" {\n" +
                "    option --release : boolean \"optimize for release.\"\n" +
                "  }\n" +
                "}");

        String script = Completion.script(spec, "demo", Completion.Shell.fish);

        assertTrue(script.contains("# demo fish shell completion"));
        assertTrue(script.contains("complete -c 'demo'"));
        assertTrue(script.contains("-l 'verbose'"));
        assertTrue(script.contains("-l 'release'"));
        assertTrue(script.contains("build"));
    }

    @Test
    public void generatesCompletionIncludingTaggedHelpSectionOptions() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --Xhelp : boolean \"shows experimental options.\" helpSection=\"experimental\"\n" +
                "  bundle xflags {\n" +
                "    group cooperative helpSection=\"experimental\" \"Experimental:%n\" {\n" +
                "      option --Xbenchmark-code-size : boolean \"benchmark code size\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "command flix \"Flix compiler.\" {\n" +
                "  command check \"Checks project.\" {\n" +
                "    option --Xhelp\n" +
                "    use xflags\n" +
                "  }\n" +
                "}");

        String fish = Completion.script(spec, "flix", Completion.Shell.fish);
        assertTrue(fish.contains("-l 'Xbenchmark-code-size'"));
        assertTrue(fish.contains("-l 'Xhelp'"));

        String bash = Completion.bashScript(spec, "flix");
        assertTrue(bash.contains("--Xbenchmark-code-size"));
        assertTrue(bash.contains("--Xhelp"));
    }

    @Test
    public void generatesAPowerShellScriptMentioningEveryOptionAndSubcommand() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo \"A demo tool.\" {\n" +
                "  option --verbose : boolean \"be verbose.\"\n" +
                "  command build \"Builds the thing.\" {\n" +
                "    option --release : boolean \"optimize for release.\"\n" +
                "  }\n" +
                "}");

        String script = Completion.script(spec, "demo", Completion.Shell.powershell);

        assertTrue(script.contains("# demo PowerShell completion"));
        assertTrue(script.contains("Register-ArgumentCompleter -Native -CommandName 'demo'"));
        assertTrue(script.contains("'--verbose'"));
        assertTrue(script.contains("'--release'"));
        assertTrue(script.contains("'build'"));
    }
}
