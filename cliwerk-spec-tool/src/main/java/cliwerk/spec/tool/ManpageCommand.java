package cliwerk.spec.tool;

import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "manpage", mixinStandardHelpOptions = true,
        description = "Generates AsciiDoc man pages for the given spec and every one of its subcommands, "
                + "via picocli-codegen's ManPageGenerator.")
final class ManpageCommand implements Callable<Integer> {

    @Spec
    CommandSpec self;

    @Parameters(index = "0", paramLabel = "<spec-file>", description = "A .cliwerk or .json spec file.")
    File specFile;

    @Option(names = {"-d", "--outdir"}, defaultValue = ".", paramLabel = "<dir>",
            description = "Output directory to write the generated AsciiDoc files to (default: the current directory).")
    File outputDirectory;

    @Option(names = "--overwrite",
            description = "Replace man pages that already exist in the output directory. "
                    + "Without this, the command writes nothing at all if any of them is already there.")
    boolean overwrite;

    @Option(names = {"-v", "--verbose"}, description = "Specify multiple -v options to increase verbosity.")
    boolean[] verbosity = new boolean[0];

    public Integer call() throws Exception {
        CommandSpec spec = SpecLoader.load(specFile);
        if (!overwrite) {
            List<File> existing = Manpage.existingPages(spec, outputDirectory);
            if (!existing.isEmpty()) {
                self.commandLine().getErr().println("Refusing to overwrite " + existing.size()
                        + " existing man page(s) in " + outputDirectory + ": " + existing
                        + ". Re-run with --overwrite to replace them.");
                return ExitCode.USAGE;
            }
        }
        return Manpage.generate(spec, outputDirectory, verbosity);
    }
}
