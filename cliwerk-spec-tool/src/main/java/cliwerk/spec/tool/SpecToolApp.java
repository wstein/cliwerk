package cliwerk.spec.tool;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * A picocli command line tool for working with cliwerk-spec DSL/JSON files: preview the CLI a
 * spec produces, generate a shell completion script or AsciiDoc man pages for it, or just check
 * that it loads. Built with picocli itself in the ordinary, annotated way -- unlike the specs it
 * operates on, this outer tool is not itself described by a cliwerk-spec file.
 */
@Command(name = "cliwerk-spec", mixinStandardHelpOptions = true, version = "cliwerk-spec " + CommandLine.VERSION,
        subcommands = {PreviewCommand.class, CompletionCommand.class, ManpageCommand.class, ValidateCommand.class, ConvertCommand.class},
        description = "Preview, validate, convert, and generate shell completion scripts and man pages "
                + "from cliwerk-spec DSL/JSON spec files.")
public final class SpecToolApp implements Runnable {

    @Spec
    CommandSpec spec;

    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new SpecToolApp()).execute(args));
    }
}
