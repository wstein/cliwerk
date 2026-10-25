package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CommandSpecJsonTest {

    @Test
    public void readsNameAndDescription() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"description\": [\"The Flix programming language\"] }");
        assertEquals("flix", spec.name());
        assertArrayEquals(new String[] {"The Flix programming language"}, spec.usageMessage().description());
    }

    @Test
    public void readsOptions() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [" +
                "{ \"names\": [\"-v\", \"--verbose\"], \"type\": \"boolean\", \"description\": [\"Enable verbose output\"] }," +
                "{ \"names\": [\"-o\", \"--output\"], \"type\": \"String\", \"defaultValue\": \"build\" }," +
                "{ \"names\": [\"-t\", \"--target\"], \"type\": \"String\", \"required\": true, \"arity\": \"1\" }" +
                "]}");

        assertEquals(3, spec.options().size());
        OptionSpec verbose = spec.findOption("--verbose");
        assertArrayEquals(new String[] {"-v", "--verbose"}, verbose.names());
        assertEquals(boolean.class, verbose.type());
        assertFalse(verbose.required());

        OptionSpec output = spec.findOption("--output");
        assertEquals(String.class, output.type());
        assertEquals("build", output.defaultValue());
        assertFalse(output.required());

        OptionSpec target = spec.findOption("--target");
        assertTrue(target.required());
        assertEquals("1", target.arity().toString());
    }

    @Test
    public void readsPositionalParams() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"positionalParams\": [" +
                "{ \"paramLabel\": \"<files>\", \"type\": \"File\", \"arity\": \"0..*\", \"description\": [\"Input files\"] }" +
                "]}");

        assertEquals(1, spec.positionalParameters().size());
        PositionalParamSpec files = spec.positionalParameters().get(0);
        assertEquals("<files>", files.paramLabel());
        assertEquals(File.class, files.type());
        assertEquals("0..*", files.arity().toString());
        assertArrayEquals(new String[] {"Input files"}, files.description());
    }

    /**
     * Regression test: {@link #readsPositionalParams} uses a scalar type, which declares the
     * right arity/paramLabel but cannot actually collect more than one value -- picocli throws
     * UnmatchedArgumentException on the second value. An array type ("File[]") is required for a
     * multi-value positional to actually work.
     */
    @Test
    public void arrayTypePositionalActuallyCollectsMultipleValues() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"positionalParams\": [" +
                "{ \"paramLabel\": \"<files>\", \"type\": \"File[]\", \"arity\": \"0..*\" }" +
                "]}");

        PositionalParamSpec files = spec.positionalParameters().get(0);
        assertEquals(File[].class, files.type());

        new CommandLine(spec).parseArgs("a.flix", "b.flix", "c.flix");

        assertArrayEquals(new File[] {new File("a.flix"), new File("b.flix"), new File("c.flix")}, (File[]) files.getValue());
    }

    @Test
    public void writingAnArrayTypeRoundTripsThroughTheSuffixedName() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [" +
                "{ \"names\": [\"--tag\"], \"type\": \"String[]\", \"arity\": \"0..*\" }" +
                "]}");

        String json = CommandSpecJson.write(spec);

        assertTrue(json.contains("\"String[]\""));
        assertEquals(String[].class, CommandSpecJson.read(json).findOption("--tag").type());
    }

    @Test
    public void readsNestedSubcommands() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"build\", \"description\": [\"Compile the project\"], \"options\": [" +
                "  { \"names\": [\"--release\"], \"type\": \"boolean\" } ] }," +
                "{ \"name\": \"run\", \"positionalParams\": [ { \"paramLabel\": \"<args>\", \"type\": \"String\", \"arity\": \"0..*\" } ] }" +
                "]}");

        assertEquals(2, spec.subcommands().size());
        CommandSpec build = spec.subcommands().get("build").getCommandSpec();
        assertEquals("build", build.name());
        assertArrayEquals(new String[] {"Compile the project"}, build.usageMessage().description());
        assertEquals(1, build.options().size());
        assertEquals("--release", build.options().get(0).longestName());

        CommandSpec run = spec.subcommands().get("run").getCommandSpec();
        assertEquals(1, run.positionalParameters().size());
    }

    @Test
    public void builtCommandLineActuallyParsesArguments() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", " +
                "\"options\": [ { \"names\": [\"-v\", \"--verbose\"], \"type\": \"boolean\" } ], " +
                "\"positionalParams\": [ { \"paramLabel\": \"<files>\", \"type\": \"String\", \"arity\": \"0..*\" } ], " +
                "\"subcommands\": [ { \"name\": \"build\", \"options\": [ { \"names\": [\"--release\"], \"type\": \"boolean\" } ] } ] }");

        CommandLine cmd = new CommandLine(spec);
        ParseResult result = cmd.parseArgs("build", "--release");
        assertTrue(result.hasSubcommand());
        ParseResult sub = result.subcommand();
        assertTrue(sub.matchedOptionValue("--release", Boolean.FALSE));
    }

    @Test
    public void readsAndWritesHiddenOnOptionsAndPositionals() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", " +
                "\"options\": [ { \"names\": [\"--Xfoo\"], \"type\": \"boolean\", \"hidden\": true }, " +
                "{ \"names\": [\"--bar\"], \"type\": \"boolean\" } ], " +
                "\"positionalParams\": [ { \"paramLabel\": \"<secret>\", \"type\": \"String\", \"hidden\": true } ] }");

        assertTrue(spec.findOption("--Xfoo").hidden());
        assertFalse(spec.findOption("--bar").hidden());
        assertTrue(spec.positionalParameters().get(0).hidden());

        String json = CommandSpecJson.write(spec);
        assertTrue(json.contains("\"hidden\": true"));
        assertTrue(CommandSpecJson.read(json).findOption("--Xfoo").hidden());
    }

    @Test
    public void readsInheritScopeOnOptionsAndPositionals() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", " +
                "\"options\": [ { \"names\": [\"--verbose\"], \"type\": \"boolean\", \"scope\": \"inherit\" }, " +
                "{ \"names\": [\"--config\"], \"type\": \"String\" } ], " +
                "\"positionalParams\": [ { \"paramLabel\": \"<files>\", \"type\": \"File[]\", \"scope\": \"inherit\" } ]," +
                "\"subcommands\": [ { \"name\": \"build\" } ] }");

        assertEquals(picocli.CommandLine.ScopeType.INHERIT, spec.findOption("--verbose").scopeType());
        assertEquals(picocli.CommandLine.ScopeType.LOCAL, spec.findOption("--config").scopeType());
        assertEquals(picocli.CommandLine.ScopeType.INHERIT, spec.positionalParameters().get(0).scopeType());
    }

    @Test
    public void inheritedOptionActuallyWorksOnASubcommand() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", " +
                "\"options\": [ { \"names\": [\"--verbose\"], \"type\": \"boolean\", \"scope\": \"inherit\" } ], " +
                "\"subcommands\": [ { \"name\": \"build\" } ] }");

        CommandLine cmd = new CommandLine(spec);
        ParseResult result = cmd.parseArgs("build", "--verbose");

        assertTrue(result.hasSubcommand());
        assertTrue(result.subcommand().matchedOptionValue("--verbose", Boolean.FALSE));
    }

    @Test
    public void writingInheritScopeRoundTrips() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [" +
                "{ \"names\": [\"--verbose\"], \"type\": \"boolean\", \"scope\": \"inherit\" }" +
                "]}");

        String json = CommandSpecJson.write(spec);

        assertTrue(json.contains("\"scope\": \"inherit\""));
        assertEquals(picocli.CommandLine.ScopeType.INHERIT, CommandSpecJson.read(json).findOption("--verbose").scopeType());
    }

    @Test
    public void readsUsageHelpAndVersionHelpFlags() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [" +
                "{ \"names\": [\"--help\"], \"type\": \"boolean\", \"usageHelp\": true }," +
                "{ \"names\": [\"--version\"], \"type\": \"boolean\", \"versionHelp\": true }," +
                "{ \"names\": [\"--json\"], \"type\": \"boolean\" }" +
                "]}");

        assertTrue(spec.findOption("--help").usageHelp());
        assertFalse(spec.findOption("--help").versionHelp());
        assertTrue(spec.findOption("--version").versionHelp());
        assertFalse(spec.findOption("--json").usageHelp());
        assertFalse(spec.findOption("--json").versionHelp());
    }

    @Test
    public void writingUsageHelpAndVersionHelpRoundTrips() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [" +
                "{ \"names\": [\"--help\"], \"type\": \"boolean\", \"usageHelp\": true }" +
                "]}");

        String json = CommandSpecJson.write(spec);

        assertTrue(json.contains("\"usageHelp\": true"));
        assertTrue(CommandSpecJson.read(json).findOption("--help").usageHelp());
    }

    @Test
    public void usageHelpActuallyShortCircuitsExecution() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [" +
                "{ \"names\": [\"--help\"], \"type\": \"boolean\", \"usageHelp\": true }" +
                "]}");

        CommandLine cmd = new CommandLine(spec);
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode);
        assertTrue(cmd.getParseResult().isUsageHelpRequested());
    }

    @Test
    public void rejectsUnknownType() {
        try {
            CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [ { \"names\": [\"-x\"], \"type\": \"Frobnicator\" } ] }");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Frobnicator"));
        }
    }

    @Command(name = "greet", description = "Greets the given names",
            subcommands = { CommandSpecJsonTest.LoudSubcommand.class })
    static class GreetCommand {
        @Option(names = {"-g", "--greeting"}, description = "The greeting phrase", defaultValue = "Hello")
        String greeting;

        @Parameters(paramLabel = "<name>", description = "Name to greet", arity = "1")
        String name;
    }

    @Command(name = "loud", description = "Shouts the greeting")
    static class LoudSubcommand {
        @Option(names = "--volume")
        int volume;
    }

    @Test
    public void writesAnnotationBasedCommandSpecToJson() {
        CommandSpec spec = new CommandLine(new GreetCommand()).getCommandSpec();

        String json = CommandSpecJson.write(spec);

        CommandSpec roundTripped = CommandSpecJson.read(json);
        assertEquals("greet", roundTripped.name());
        assertArrayEquals(new String[] {"Greets the given names"}, roundTripped.usageMessage().description());

        OptionSpec greeting = roundTripped.findOption("--greeting");
        assertArrayEquals(new String[] {"-g", "--greeting"}, greeting.names());
        assertEquals(String.class, greeting.type());
        assertEquals("Hello", greeting.defaultValue());

        assertEquals(1, roundTripped.positionalParameters().size());
        assertEquals("<name>", roundTripped.positionalParameters().get(0).paramLabel());
        assertEquals("1", roundTripped.positionalParameters().get(0).arity().toString());

        assertEquals(Arrays.asList("loud"), Arrays.asList(roundTripped.subcommands().keySet().toArray()));
        CommandSpec loud = roundTripped.subcommands().get("loud").getCommandSpec();
        assertEquals("--volume", loud.options().get(0).longestName());
        assertEquals(int.class, loud.options().get(0).type());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void writesSchemaAttributeAtDocumentRoot() {
        CommandSpec spec = CommandSpec.create().name("mycmd");
        String json = CommandSpecJson.write(spec);

        assertTrue(json.contains("\"$schema\": \"" + CommandSpecJson.SCHEMA_URL + "\""));
        Map<String, Object> map = (Map<String, Object>) cliwerk.spec.json.Json.parse(json);
        assertEquals(CommandSpecJson.SCHEMA_URL, map.get("$schema"));
    }

    @Test
    public void readsMixinStandardHelpOptionsOnCommand() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"app\", \"mixinStandardHelpOptions\": true }");
        assertTrue(spec.mixinStandardHelpOptions());

        OptionSpec help = spec.findOption("--help");
        assertNotNull(help);
        assertTrue(help.usageHelp());
        assertArrayEquals(new String[] {"-h", "--help"}, help.names());

        OptionSpec version = spec.findOption("--version");
        assertNotNull(version);
        assertTrue(version.versionHelp());
        assertArrayEquals(new String[] {"-V", "--version"}, version.names());

        CommandLine cmd = new CommandLine(spec);
        ParseResult helpResult = cmd.parseArgs("-h");
        assertTrue(helpResult.isUsageHelpRequested());

        ParseResult versionResult = cmd.parseArgs("-V");
        assertTrue(versionResult.isVersionHelpRequested());
    }

    @Test
    public void readsMixinStandardHelpOptionsOnSubcommand() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"app\", \"subcommands\": [" +
                "{ \"name\": \"sub\", \"mixinStandardHelpOptions\": true }" +
                "] }");

        assertFalse(spec.mixinStandardHelpOptions());
        CommandSpec sub = spec.subcommands().get("sub").getCommandSpec();
        assertTrue(sub.mixinStandardHelpOptions());
        assertNotNull(sub.findOption("--help"));
        assertNotNull(sub.findOption("--version"));
    }

    @Test
    public void writesMixinStandardHelpOptionsAndOmitsStandardOptionsFromFlatList() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.mixinStandardHelpOptions(true);
        spec.addOption(OptionSpec.builder("-v", "--verbose").type(boolean.class).build());

        String json = CommandSpecJson.write(spec);
        assertTrue(json.contains("\"mixinStandardHelpOptions\": true"));

        CommandSpec read = CommandSpecJson.read(json);
        assertTrue(read.mixinStandardHelpOptions());
        assertNotNull(read.findOption("--verbose"));
        assertNotNull(read.findOption("--help"));
        assertNotNull(read.findOption("--version"));
        assertEquals(3, read.options().size());
    }
}
