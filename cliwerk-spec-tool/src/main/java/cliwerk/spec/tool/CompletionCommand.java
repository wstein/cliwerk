package cliwerk.spec.tool;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(name = "completion", mixinStandardHelpOptions = true,
        description = "Generates a bash/zsh, fish or PowerShell completion script for the given spec.")
final class CompletionCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<spec-file>", description = "A .cliwerk or .json spec file.")
    File specFile;

    @Option(names = {"-n", "--name"},
            description = "The name of the command to generate a completion script for (default: the spec's own root command name).")
    String scriptName;

    @Option(names = {"-s", "--shell"},
            description = "The shell to generate a completion script for: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
            defaultValue = "bash")
    Completion.Shell shell;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Write the script to this file instead of stdout.")
    File outputFile;

    @Spec
    CommandSpec self;

    public Integer call() throws Exception {
        CommandSpec spec = SpecLoader.load(specFile);
        String name = scriptName != null ? scriptName : spec.name();
        String script = Completion.script(spec, name, shell);

        if (outputFile != null) {
            Files.write(outputFile.toPath(), script.getBytes(StandardCharsets.UTF_8));
        } else {
            PrintWriter out = self.commandLine().getOut();
            out.print(script);
            out.flush();
        }
        return 0;
    }
}
