package cliwerk.spec.tool;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Generates a shell completion script for a {@link CommandSpec}, via picocli's own
 * {@link AutoComplete}. Closes the gap between what this module already delivers (a beautiful,
 * {@code --help}-enabled proxy CLI) and its originally stated goal of also delivering completion.
 */
final class Completion {

    public enum Shell {
        bash, fish, powershell
    }

    private Completion() {
    }

    static String script(CommandSpec spec, String scriptName, Shell shell) {
        CommandLine commandLine = new CommandLine(spec);
        if (shell == Shell.fish) {
            return AutoComplete.fish(scriptName, commandLine);
        }
        if (shell == Shell.powershell) {
            return AutoComplete.powershell(scriptName, commandLine);
        }
        return AutoComplete.bash(scriptName, commandLine);
    }

    static String bashScript(CommandSpec spec, String scriptName) {
        return script(spec, scriptName, Shell.bash);
    }
}
