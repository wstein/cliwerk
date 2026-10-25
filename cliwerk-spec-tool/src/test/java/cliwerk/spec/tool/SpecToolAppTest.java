package cliwerk.spec.tool;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end tests exercising {@link SpecToolApp} exactly as a user would invoke it, through
 * {@link CommandLine#execute(String...)} rather than calling any subcommand's logic directly.
 */
public class SpecToolAppTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File specFile(String content) throws Exception {
        File file = tmp.newFile("spec.cliwerk");
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static final class Captured {
        final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exitCode;

        String out() throws Exception { return outBytes.toString("UTF-8"); }
        String err() throws Exception { return errBytes.toString("UTF-8"); }
    }

    private Captured run(String... args) throws Exception {
        Captured captured = new Captured();
        CommandLine cmd = new CommandLine(new SpecToolApp());
        cmd.setOut(new PrintWriter(new OutputStreamWriter(captured.outBytes, "UTF-8"), true));
        cmd.setErr(new PrintWriter(new OutputStreamWriter(captured.errBytes, "UTF-8"), true));
        captured.exitCode = cmd.execute(args);
        return captured;
    }

    @Test
    public void noArgumentsPrintsRootUsage() throws Exception {
        Captured result = run();

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(result.out().contains("cliwerk-spec"));
        assertTrue(result.out().contains("preview"));
        assertTrue(result.out().contains("completion"));
        assertTrue(result.out().contains("manpage"));
        assertTrue(result.out().contains("validate"));
        assertTrue(result.out().contains("convert"));
    }

    @Test
    public void previewPrintsUsageForTheSpecAndItsSubcommands() throws Exception {
        File spec = specFile(
                "command demo \"A demo tool.\" {\n" +
                "  command build \"Builds the thing.\" {}\n" +
                "}");

        Captured result = run("preview", spec.getPath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(result.out().contains("A demo tool."));
        assertTrue(result.out().contains("Builds the thing."));
    }

    @Test
    public void completionWritesABashScriptToStdoutByDefault() throws Exception {
        File spec = specFile("command demo \"A demo tool.\" { option --verbose : boolean \"be verbose.\" }");

        Captured result = run("completion", spec.getPath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(result.out().contains("#!/usr/bin/env bash"));
        assertTrue(result.out().contains("--verbose"));
    }

    @Test
    public void completionWritesAFishScriptWhenRequested() throws Exception {
        File spec = specFile("command demo \"A demo tool.\" { option --verbose : boolean \"be verbose.\" }");

        Captured result = run("completion", "--shell", "fish", spec.getPath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(result.out().contains("# demo fish shell completion"));
        assertTrue(result.out().contains("complete -c 'demo'"));
        assertTrue(result.out().contains("-l 'verbose'"));
    }

    @Test
    public void completionWritesToAFileWhenOutputIsGiven() throws Exception {
        File spec = specFile("command demo \"A demo tool.\" {}");
        File outputFile = new File(tmp.getRoot(), "demo-completion.sh");

        Captured result = run("completion", "--output", outputFile.getPath(), spec.getPath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertEquals("", result.out());
        assertTrue(outputFile.exists());
        String content = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("#!/usr/bin/env bash"));
    }

    @Test
    public void manpageWritesAsciiDocFilesToTheOutputDirectory() throws Exception {
        File spec = specFile("command demo \"A demo tool.\" {}");

        Captured result = run("manpage", "--outdir", tmp.getRoot().getPath(), spec.getPath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(new File(tmp.getRoot(), "demo.adoc").exists());
    }

    @Test
    public void manpageRefusesToOverwriteExistingFilesAndLeavesThemUntouched() throws Exception {
        File spec = specFile("command demo \"A demo tool.\" {}");
        File page = new File(tmp.getRoot(), "demo.adoc");
        Files.write(page.toPath(), "hand-edited".getBytes(StandardCharsets.UTF_8));

        Captured result = run("manpage", "--outdir", tmp.getRoot().getPath(), spec.getPath());

        assertEquals(ExitCode.USAGE, result.exitCode);
        assertTrue(result.err(), result.err().contains("demo.adoc"));
        assertTrue(result.err(), result.err().contains("--overwrite"));
        assertEquals("hand-edited", new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void manpageOverwritesExistingFilesWhenAskedTo() throws Exception {
        File spec = specFile("command demo \"A demo tool.\" {}");
        File page = new File(tmp.getRoot(), "demo.adoc");
        Files.write(page.toPath(), "hand-edited".getBytes(StandardCharsets.UTF_8));

        Captured result = run("manpage", "--overwrite", "--outdir", tmp.getRoot().getPath(), spec.getPath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8).contains("A demo tool."));
    }

    @Test
    public void validatePrintsOkForAValidSpec() throws Exception {
        File spec = specFile("command demo \"A demo tool.\" {}");

        Captured result = run("validate", spec.getPath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(result.out().contains("OK"));
    }

    @Test
    public void validatePrintsAnErrorForAMalformedSpec() throws Exception {
        File spec = specFile("command demo { option --nope }");

        Captured result = run("validate", spec.getPath());

        assertEquals(ExitCode.SOFTWARE, result.exitCode);
        assertTrue(result.err().contains("--nope"));
    }
}
