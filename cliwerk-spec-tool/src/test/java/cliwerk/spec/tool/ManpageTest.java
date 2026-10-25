package cliwerk.spec.tool;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ExitCode;
import cliwerk.spec.CommandSpecDsl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ManpageTest {

    @Rule
    public TemporaryFolder outdir = new TemporaryFolder();

    @Test
    public void generatesAnAsciiDocFileForTheRootCommandAndEverySubcommand() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo \"A demo tool.\" {\n" +
                "  option --verbose : boolean \"be verbose.\"\n" +
                "  command build \"Builds the thing.\" {}\n" +
                "}");

        int exitCode = Manpage.generate(spec, outdir.getRoot(), new boolean[0]);

        assertEquals(ExitCode.OK, exitCode);
        File rootPage = new File(outdir.getRoot(), "demo.adoc");
        File buildPage = new File(outdir.getRoot(), "demo-build.adoc");
        assertTrue(rootPage.exists());
        assertTrue(buildPage.exists());

        String rootContent = new String(Files.readAllBytes(rootPage.toPath()), StandardCharsets.UTF_8);
        assertTrue(rootContent.contains("A demo tool."));
        assertTrue(rootContent.contains("--verbose"));
    }

    @Test
    public void listsTheManPagesThatAlreadyExistInTheOutputDirectory() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command demo {\n" +
                "  command build {}\n" +
                "  command secret {}\n" +
                "}");
        spec.subcommands().get("secret").getCommandSpec().usageMessage().hidden(true);

        assertTrue(Manpage.existingPages(spec, outdir.getRoot()).isEmpty());

        Manpage.generate(spec, outdir.getRoot(), new boolean[0]);

        List<String> names = new ArrayList<String>();
        for (File file : Manpage.existingPages(spec, outdir.getRoot())) { names.add(file.getName()); }
        Collections.sort(names);
        assertEquals(Arrays.asList("demo-build.adoc", "demo.adoc"), names);
    }

    @Test
    public void everyGeneratedFileIsPredictedByExistingPages() throws Exception {
        CommandSpec spec = CommandSpecDsl.parse(
                "command my-demo {\n" +
                "  command build {\n" +
                "    command again {}\n" +
                "  }\n" +
                "}");

        Manpage.generate(spec, outdir.getRoot(), new boolean[0]);

        List<String> generated = new ArrayList<String>(Arrays.asList(outdir.getRoot().list()));
        List<String> predicted = new ArrayList<String>();
        for (File file : Manpage.existingPages(spec, outdir.getRoot())) { predicted.add(file.getName()); }
        Collections.sort(generated);
        Collections.sort(predicted);
        assertEquals(generated, predicted);
    }
}
