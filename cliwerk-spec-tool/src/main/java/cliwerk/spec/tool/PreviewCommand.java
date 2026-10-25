package cliwerk.spec.tool;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "preview", mixinStandardHelpOptions = true,
        description = "Prints the usage help message for the given spec, and every subcommand, recursively.")
final class PreviewCommand implements Callable<Integer> {

    enum Format { text, markdown }

    @Parameters(index = "0", paramLabel = "<spec-file>", description = "A .cliwerk or .json spec file.")
    File specFile;

    @Option(names = "--ansi", description = "Force ANSI colors on/off/auto (default: auto).", defaultValue = "AUTO")
    Ansi ansi;

    @Option(names = {"-s", "--section"}, paramLabel = "<name>",
            description = "Name of an on-demand help section to additionally render for every command that "
                    + "declares it (e.g. \"experimental\"). Repeatable.")
    List<String> sections = new ArrayList<String>();

    @Option(names = "--trigger-option", paramLabel = "<option>",
            description = "Option to pass when rendering a --section, overriding automatic discovery of the "
                    + "usageHelp option tagged with that section (e.g. \"--Xhelp\").")
    String triggerOption;

    @Option(names = {"-f", "--format"},
            description = "Output format: ${COMPLETION-CANDIDATES} (default: text).", defaultValue = "text")
    Format format;

    @Spec
    CommandSpec self;

    public Integer call() throws Exception {
        CommandSpec spec = SpecLoader.load(specFile);
        Preview.render(spec, self.commandLine().getOut(), ansi, sections, triggerOption, format);
        self.commandLine().getOut().flush(); // PrintWriter autoFlush only triggers on println/printf/format
        return 0;
    }
}
