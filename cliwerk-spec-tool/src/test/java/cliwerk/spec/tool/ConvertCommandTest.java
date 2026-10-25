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

public class ConvertCommandTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File file(String name, String content) throws Exception {
        File f = tmp.newFile(name);
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
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
    public void convertsDslToJsonOnStdout() throws Exception {
        File dsl = file("demo.cliwerk", "command demo \"A demo command\" {\n" +
                "  option -v, --verbose : boolean \"Verbose flag\"\n" +
                "}");

        Captured result = run("convert", dsl.getAbsolutePath(), "--to=json");

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(result.out().contains("\"name\": \"demo\""));
        assertTrue(result.out().contains("\"--verbose\""));
    }

    @Test
    public void convertsJsonToDslOnStdout() throws Exception {
        File json = file("demo.json", "{\n" +
                "  \"name\": \"demo\",\n" +
                "  \"options\": [\n" +
                "    { \"names\": [\"-v\", \"--verbose\"], \"type\": \"boolean\" }\n" +
                "  ]\n" +
                "}");

        Captured result = run("convert", json.getAbsolutePath(), "--to=dsl");

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(result.out().contains("command demo {"));
        assertTrue(result.out().contains("option -v, --verbose : boolean"));
    }

    @Test
    public void autoDeducesOppositeFormatWhenToIsOmitted() throws Exception {
        File dsl = file("demo.cliwerk", "command demo {\n" +
                "  option --flag : boolean\n" +
                "}");
        Captured resultDsl = run("convert", dsl.getAbsolutePath());
        assertEquals(ExitCode.OK, resultDsl.exitCode);
        assertTrue(resultDsl.out().contains("\"name\": \"demo\""));

        File json = file("demo.json", "{\n" +
                "  \"name\": \"demo\",\n" +
                "  \"options\": [ { \"names\": [\"--flag\"], \"type\": \"boolean\" } ]\n" +
                "}");
        Captured resultJson = run("convert", json.getAbsolutePath());
        assertEquals(ExitCode.OK, resultJson.exitCode);
        assertTrue(resultJson.out().contains("command demo {"));
    }

    @Test
    public void writesToFileWithOutputOption() throws Exception {
        File dsl = file("demo.cliwerk", "command demo {\n" +
                "  option -f, --file : File\n" +
                "}");
        File outFile = new File(tmp.getRoot(), "out.json");

        Captured result = run("convert", dsl.getAbsolutePath(), "-o", outFile.getAbsolutePath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(outFile.exists());
        String content = new String(Files.readAllBytes(outFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"name\": \"demo\""));
    }

    @Test
    public void deducesTargetFormatFromOutputFileExtension() throws Exception {
        File json = file("demo.json", "{\n" +
                "  \"name\": \"demo\",\n" +
                "  \"options\": [ { \"names\": [\"--flag\"], \"type\": \"boolean\" } ]\n" +
                "}");
        File outDsl = new File(tmp.getRoot(), "out.cliwerk");

        Captured result = run("convert", json.getAbsolutePath(), "-o", outDsl.getAbsolutePath());

        assertEquals(ExitCode.OK, result.exitCode);
        assertTrue(outDsl.exists());
        String content = new String(Files.readAllBytes(outDsl.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("command demo {"));
        assertTrue(content.contains("option --flag : boolean"));
    }
}
