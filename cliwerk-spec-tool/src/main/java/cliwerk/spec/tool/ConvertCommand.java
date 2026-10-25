package cliwerk.spec.tool;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import cliwerk.spec.CommandSpecDsl;
import cliwerk.spec.CommandSpecJson;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(name = "convert", mixinStandardHelpOptions = true,
        description = "Converts a spec file between DSL and JSON formats.")
final class ConvertCommand implements Callable<Integer> {

    enum Format { dsl, json }

    @Parameters(index = "0", paramLabel = "<spec-file>", description = "A .cliwerk or .json spec file.")
    File specFile;

    @Option(names = {"-t", "--to"},
            description = "Target format: ${COMPLETION-CANDIDATES} (default: deduced from --output extension or the opposite of input format).")
    Format to;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Write output to this file instead of stdout.")
    File outputFile;

    @Spec
    CommandSpec self;

    public Integer call() throws Exception {
        CommandSpec spec = SpecLoader.load(specFile);
        Format target = resolveTargetFormat();
        String text;
        if (target == Format.dsl) {
            text = CommandSpecDsl.write(spec);
        } else {
            text = CommandSpecJson.write(spec) + "\n";
        }

        if (outputFile != null) {
            Files.write(outputFile.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } else {
            PrintWriter out = self.commandLine().getOut();
            out.print(text);
            out.flush();
        }
        return 0;
    }

    private Format resolveTargetFormat() {
        if (to != null) {
            return to;
        }
        if (outputFile != null) {
            String name = outputFile.getName();
            if (name.endsWith(".json")) {
                return Format.json;
            }
            if (name.endsWith(".cliwerk") || name.endsWith(".dsl")) {
                return Format.dsl;
            }
        }
        String inputName = specFile.getName();
        if (inputName.endsWith(".json")) {
            return Format.dsl;
        }
        if (inputName.endsWith(".cliwerk") || inputName.endsWith(".dsl")) {
            return Format.json;
        }
        throw new IllegalArgumentException("Unable to deduce target format; specify --to=dsl or --to=json");
    }
}
