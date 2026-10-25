package cliwerk.spec.tool;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.codegen.docgen.manpage.ManPageGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates AsciiDoc man pages for a {@link CommandSpec} and every one of its subcommands, via
 * picocli-codegen's {@link ManPageGenerator} -- which already accepts plain {@code CommandSpec}
 * instances, with no annotated Java class required, so it works directly against a spec built
 * from a cliwerk-spec DSL/JSON file.
 */
final class Manpage {

    private Manpage() {
    }

    static int generate(CommandSpec spec, File outputDirectory, boolean[] verbosity) throws IOException {
        // ManPageGenerator calls spec.commandLine(), which a freshly-parsed root CommandSpec
        // doesn't have set yet -- addSubcommand() wraps every SUBCOMMAND spec in its own
        // CommandLine already, but nothing does that for the root until something wraps it.
        new CommandLine(spec);
        return ManPageGenerator.generateManPage(outputDirectory, null, verbosity, false, spec);
    }

    /**
     * Returns the files in {@code outputDirectory} that {@link #generate} would overwrite.
     * ManPageGenerator has no such check of its own and its file naming is private, so this
     * mirrors it; {@code ManpageTest} asserts the two stay in step.
     */
    static List<File> existingPages(CommandSpec spec, File outputDirectory) {
        List<File> result = new ArrayList<File>();
        for (String fileName : fileNames(spec, new HashSet<CommandSpec>())) {
            File file = new File(outputDirectory, fileName);
            if (file.exists()) { result.add(file); }
        }
        return result;
    }

    private static List<String> fileNames(CommandSpec spec, Set<CommandSpec> done) {
        List<String> result = new ArrayList<String>();
        if (!done.add(spec)) { return result; }
        result.add((spec.qualifiedName("-") + ".adoc").replaceAll("\\s", "_").replace("<main_class>", "main_class"));
        for (CommandLine sub : spec.subcommands().values()) {
            CommandSpec subSpec = sub.getCommandSpec();
            if (subSpec.usageMessage().hidden()) { continue; }
            result.addAll(fileNames(subSpec, done));
        }
        return result;
    }
}
