package cliwerk.spec.tool;

import picocli.CommandLine.Model.CommandSpec;
import cliwerk.spec.CommandSpecDsl;
import cliwerk.spec.CommandSpecJson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Loads a {@link CommandSpec} from a cliwerk-spec file, choosing the DSL parser or the JSON
 * reader based on the file's extension ({@code .cliwerk}/{@code .dsl} vs {@code .json}). Shared by
 * every subcommand of this tool, since all of them start from "here is a spec file" the same way.
 */
final class SpecLoader {

    private SpecLoader() {
    }

    static CommandSpec load(File specFile) throws IOException {
        String text = new String(Files.readAllBytes(specFile.toPath()), StandardCharsets.UTF_8);
        String name = specFile.getName();
        if (name.endsWith(".json")) {
            return CommandSpecJson.read(text);
        }
        if (name.endsWith(".cliwerk") || name.endsWith(".dsl")) {
            return CommandSpecDsl.parse(text);
        }
        throw new IllegalArgumentException(
                "Unrecognized spec file extension: \"" + name + "\" (expected \".cliwerk\" or \".json\")");
    }
}
