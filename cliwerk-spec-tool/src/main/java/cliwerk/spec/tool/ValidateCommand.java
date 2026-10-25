package cliwerk.spec.tool;

import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "validate", mixinStandardHelpOptions = true,
        description = "Checks that the given spec file loads successfully, without generating anything.")
final class ValidateCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<spec-file>", description = "A .cliwerk or .json spec file.")
    File specFile;

    @Spec
    CommandSpec self;

    public Integer call() {
        String error = Validate.check(specFile);
        if (error == null) {
            PrintWriter out = self.commandLine().getOut();
            out.println(specFile + ": OK");
            out.flush();
            return ExitCode.OK;
        }
        PrintWriter err = self.commandLine().getErr();
        err.println(specFile + ": " + error);
        err.flush();
        return ExitCode.SOFTWARE;
    }
}
