package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CommandSpecMergerTest {

    private static CommandSpec uberSpec() {
        CommandSpec uber = CommandSpec.create();
        uber.name("uber");
        uber.addOption(OptionSpec.builder("--config").type(String.class).build());
        return uber;
    }

    private static CommandSpec flixSpec() {
        return CommandSpecJson.read("{ \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"build\", \"options\": [ { \"names\": [\"--release\"], \"type\": \"boolean\" } ] } ] }");
    }

    private static CommandSpec otherToolSpec() {
        return CommandSpecJson.read("{ \"name\": \"other-tool\", \"options\": [ { \"names\": [\"--fast\"], \"type\": \"boolean\" } ] }");
    }

    @Test
    public void attachesSingleImportedSpecAsSubcommand() {
        CommandSpec uber = uberSpec();

        CommandSpec merged = CommandSpecMerger.merge(uber, flixSpec());

        assertSame(uber, merged);
        assertTrue(merged.subcommands().containsKey("flix"));
        assertEquals("--config", merged.options().get(0).longestName());
    }

    @Test
    public void mergedUberActuallyParsesAndDelegates() {
        CommandSpec uber = CommandSpecMerger.merge(uberSpec(), flixSpec());
        CommandLine cmd = new CommandLine(uber);

        ParseResult result = cmd.parseArgs("--config", "cfg.json", "flix", "build", "--release");

        assertTrue(result.hasMatchedOption("--config"));
        assertEquals("cfg.json", result.matchedOptionValue("--config", (String) null));
        assertTrue(result.hasSubcommand());
        ParseResult flix = result.subcommand();
        assertTrue(flix.hasSubcommand());
        ParseResult build = flix.subcommand();
        assertTrue(build.matchedOptionValue("--release", Boolean.FALSE));
    }

    @Test
    public void attachesMultipleImportedSpecsInOneCall() {
        CommandSpec merged = CommandSpecMerger.merge(uberSpec(), flixSpec(), otherToolSpec());

        assertTrue(merged.subcommands().containsKey("flix"));
        assertTrue(merged.subcommands().containsKey("other-tool"));
    }

    @Test
    public void mergeAllAttachesCollectionOfSpecs() {
        CommandSpec uber = uberSpec();
        java.util.List<CommandSpec> specs = java.util.Arrays.asList(flixSpec(), otherToolSpec());

        CommandSpec merged = CommandSpecMerger.mergeAll(uber, specs);

        assertSame(uber, merged);
        assertTrue(merged.subcommands().containsKey("flix"));
        assertTrue(merged.subcommands().containsKey("other-tool"));
    }

    @Test
    public void chainingIsSemanticallyEquivalentToVarargsAndMergeAll() {
        CommandSpec varargs = CommandSpecMerger.merge(uberSpec(), flixSpec(), otherToolSpec());
        CommandSpec chained = CommandSpecMerger.merge(CommandSpecMerger.merge(uberSpec(), flixSpec()), otherToolSpec());
        CommandSpec all = CommandSpecMerger.mergeAll(uberSpec(), java.util.Arrays.asList(flixSpec(), otherToolSpec()));

        assertEquals(varargs.subcommands().keySet(), chained.subcommands().keySet());
        assertEquals(varargs.subcommands().keySet(), all.subcommands().keySet());

        for (String subName : varargs.subcommands().keySet()) {
            CommandSpec varargsSub = varargs.subcommands().get(subName).getCommandSpec();
            CommandSpec chainedSub = chained.subcommands().get(subName).getCommandSpec();
            CommandSpec allSub = all.subcommands().get(subName).getCommandSpec();

            assertEquals(varargsSub.options().size(), chainedSub.options().size());
            assertEquals(varargsSub.options().size(), allSub.options().size());
        }

        String[] testArgs = {"--config", "cfg.json", "flix", "build", "--release"};
        assertTrue(new CommandLine(varargs).parseArgs(testArgs).subcommand().subcommand().matchedOptionValue("--release", Boolean.FALSE));
        assertTrue(new CommandLine(chained).parseArgs(testArgs).subcommand().subcommand().matchedOptionValue("--release", Boolean.FALSE));
        assertTrue(new CommandLine(all).parseArgs(testArgs).subcommand().subcommand().matchedOptionValue("--release", Boolean.FALSE));
    }

    @Test
    public void rejectsDuplicateSubcommandName() {
        CommandSpec uber = CommandSpecMerger.merge(uberSpec(), flixSpec());
        try {
            CommandSpecMerger.merge(uber, flixSpec());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("flix"));
        }
    }

    @Test
    public void rejectsImportedSpecWithoutName() {
        CommandSpec unnamed = CommandSpec.create();
        try {
            CommandSpecMerger.merge(uberSpec(), unnamed);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
