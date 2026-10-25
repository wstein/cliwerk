package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CommandSpecDslWriteTest {

    @Test
    public void writesBasicCommandWithNameAndDescription() {
        CommandSpec spec = CommandSpec.create().name("flix");
        spec.usageMessage().description("The Flix programming language");

        String dsl = CommandSpecDsl.write(spec);

        CommandSpec parsed = CommandSpecDsl.parse(dsl);
        assertEquals("flix", parsed.name());
        assertArrayEquals(new String[] {"The Flix programming language"}, parsed.usageMessage().description());
    }

    @Test
    public void writesCommandWithoutDescription() {
        CommandSpec spec = CommandSpec.create().name("flix");

        String dsl = CommandSpecDsl.write(spec);

        CommandSpec parsed = CommandSpecDsl.parse(dsl);
        assertEquals("flix", parsed.name());
        assertEquals(0, parsed.usageMessage().description().length);
    }

    @Test
    public void writesOptionsWithAttributes() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.addOption(OptionSpec.builder("-v", "--verbose")
                .type(boolean.class)
                .description("Enable verbose output")
                .build());
        spec.addOption(OptionSpec.builder("-o", "--output")
                .type(String.class)
                .description("Output directory")
                .defaultValue("build")
                .build());
        spec.addOption(OptionSpec.builder("-t", "--target")
                .type(String.class)
                .required(true)
                .build());
        spec.addOption(OptionSpec.builder("--tags")
                .type(String[].class)
                .arity("0..*")
                .build());
        spec.addOption(OptionSpec.builder("--help")
                .type(boolean.class)
                .usageHelp(true)
                .build());
        spec.addOption(OptionSpec.builder("--version")
                .type(boolean.class)
                .versionHelp(true)
                .build());
        spec.addOption(OptionSpec.builder("--config")
                .type(File.class)
                .scopeType(CommandLine.ScopeType.INHERIT)
                .build());
        spec.addOption(OptionSpec.builder("--secret")
                .type(String.class)
                .hidden(true)
                .build());

        String dsl = CommandSpecDsl.write(spec);

        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);
        assertEquals(8, roundTripped.options().size());

        OptionSpec verbose = roundTripped.findOption("--verbose");
        assertArrayEquals(new String[] {"-v", "--verbose"}, verbose.names());
        assertEquals(boolean.class, verbose.type());
        assertArrayEquals(new String[] {"Enable verbose output"}, verbose.description());

        OptionSpec output = roundTripped.findOption("--output");
        assertEquals(String.class, output.type());
        assertEquals("build", output.defaultValue());

        OptionSpec target = roundTripped.findOption("--target");
        assertTrue(target.required());

        OptionSpec tags = roundTripped.findOption("--tags");
        assertEquals(String[].class, tags.type());
        assertEquals("0..*", tags.arity().toString());

        OptionSpec help = roundTripped.findOption("--help");
        assertTrue(help.usageHelp());

        OptionSpec version = roundTripped.findOption("--version");
        assertTrue(version.versionHelp());

        OptionSpec config = roundTripped.findOption("--config");
        assertEquals(CommandLine.ScopeType.INHERIT, config.scopeType());

        OptionSpec secret = roundTripped.findOption("--secret");
        assertTrue(secret.hidden());
    }

    @Test
    public void writesPositionalParameters() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.addPositional(PositionalParamSpec.builder()
                .paramLabel("<mode>")
                .type(String.class)
                .defaultValue("fast")
                .build());
        spec.addPositional(PositionalParamSpec.builder()
                .paramLabel("<env>")
                .type(String.class)
                .required(true)
                .scopeType(CommandLine.ScopeType.INHERIT)
                .hidden(true)
                .build());
        spec.addPositional(PositionalParamSpec.builder()
                .paramLabel("<files>")
                .type(File[].class)
                .description("Input files")
                .arity("0..*")
                .build());

        String dsl = CommandSpecDsl.write(spec);

        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);
        assertEquals(3, roundTripped.positionalParameters().size());

        PositionalParamSpec mode = roundTripped.positionalParameters().get(0);
        assertEquals("<mode>", mode.paramLabel());
        assertEquals(String.class, mode.type());
        assertEquals("fast", mode.defaultValue());

        PositionalParamSpec env = roundTripped.positionalParameters().get(1);
        assertEquals("<env>", env.paramLabel());
        assertTrue(env.required());
        assertEquals(CommandLine.ScopeType.INHERIT, env.scopeType());
        assertTrue(env.hidden());

        PositionalParamSpec files = roundTripped.positionalParameters().get(2);
        assertEquals("<files>", files.paramLabel());
        assertEquals(File[].class, files.type());
        assertArrayEquals(new String[] {"Input files"}, files.description());
        assertEquals("0..*", files.arity().toString());
    }

    @Test
    public void writesArgGroups() {
        CommandSpec spec = CommandSpec.create().name("app");

        ArgGroupSpec exclusiveGroup = ArgGroupSpec.builder()
                .exclusive(true)
                .multiplicity("1")
                .heading("Output Format:%n")
                .addArg(OptionSpec.builder("--json").type(boolean.class).build())
                .addArg(OptionSpec.builder("--xml").type(boolean.class).build())
                .build();

        ArgGroupSpec coopGroup = ArgGroupSpec.builder()
                .exclusive(false)
                .addArg(OptionSpec.builder("--user").type(String.class).build())
                .addArg(OptionSpec.builder("--password").type(String.class).build())
                .build();

        spec.addArgGroup(exclusiveGroup);
        spec.addArgGroup(coopGroup);

        String dsl = CommandSpecDsl.write(spec);

        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);
        assertEquals(2, roundTripped.argGroups().size());

        ArgGroupSpec g1 = roundTripped.argGroups().get(0);
        assertTrue(g1.exclusive());
        assertEquals("1", g1.multiplicity().toString());
        assertEquals("Output Format:%n", g1.heading());
        assertEquals(2, g1.args().size());

        ArgGroupSpec g2 = roundTripped.argGroups().get(1);
        assertFalse(g2.exclusive());
        assertEquals(2, g2.args().size());
    }

    @Test
    public void writesNestedSubcommands() {
        CommandSpec root = CommandSpec.create().name("git");
        root.addOption(OptionSpec.builder("-v", "--verbose").type(boolean.class).build());

        CommandSpec commit = CommandSpec.create().name("commit");
        commit.usageMessage().description("Record changes to the repository");
        commit.addOption(OptionSpec.builder("-m", "--message").type(String.class).required(true).build());
        root.addSubcommand("commit", commit);

        String dsl = CommandSpecDsl.write(root);

        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);
        assertEquals(1, roundTripped.subcommands().size());
        assertTrue(roundTripped.subcommands().containsKey("commit"));

        CommandSpec sub = roundTripped.subcommands().get("commit").getCommandSpec();
        assertEquals("commit", sub.name());
        assertArrayEquals(new String[] {"Record changes to the repository"}, sub.usageMessage().description());
        OptionSpec message = sub.findOption("--message");
        assertTrue(message.required());
    }

    @Test
    public void escapesSpecialCharactersInStrings() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.usageMessage().description("Line 1\nLine 2 with \"quotes\" and \\backslash");
        spec.addOption(OptionSpec.builder("--msg")
                .type(String.class)
                .description("A\ttab\tand \"quotes\"")
                .defaultValue("hello \"world\"")
                .build());

        String dsl = CommandSpecDsl.write(spec);

        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);
        assertArrayEquals(new String[] {"Line 1", "Line 2 with \"quotes\" and \\backslash"},
                roundTripped.usageMessage().description());

        OptionSpec msg = roundTripped.findOption("--msg");
        assertArrayEquals(new String[] {"A\ttab\tand \"quotes\""}, msg.description());
        assertEquals("hello \"world\"", msg.defaultValue());
    }

    @Command(name = "greet", description = "Greets the given names")
    static class GreetCommand {
        @Option(names = {"-g", "--greeting"}, description = "The greeting phrase", defaultValue = "Hello")
        String greeting;

        @Parameters(paramLabel = "<name>", description = "Name to greet", arity = "1")
        String name;
    }

    @Test
    public void writesAnnotationBasedCommandSpecToDsl() {
        CommandSpec spec = new CommandLine(new GreetCommand()).getCommandSpec();

        String dsl = CommandSpecDsl.write(spec);

        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);
        assertEquals("greet", roundTripped.name());
        assertArrayEquals(new String[] {"Greets the given names"}, roundTripped.usageMessage().description());

        OptionSpec greeting = roundTripped.findOption("--greeting");
        assertArrayEquals(new String[] {"-g", "--greeting"}, greeting.names());
        assertEquals(String.class, greeting.type());
        assertEquals("Hello", greeting.defaultValue());

        assertEquals(1, roundTripped.positionalParameters().size());
        assertEquals("<name>", roundTripped.positionalParameters().get(0).paramLabel());
    }

    @Test
    public void roundTripsMultiLineCommandDescription() {
        CommandSpec spec = CommandSpec.create().name("flix");
        spec.usageMessage().description("The Flix programming language", "", "See https://flix.dev");

        CommandSpec parsed = CommandSpecDsl.parse(CommandSpecDsl.write(spec));

        assertArrayEquals(new String[] {"The Flix programming language", "", "See https://flix.dev"},
                parsed.usageMessage().description());
    }

    @Test
    public void roundTripsMultiLineOptionDescription() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.addOption(OptionSpec.builder("--verbose")
                .type(boolean.class)
                .description("Enable verbose output.", "May be repeated.")
                .build());

        CommandSpec parsed = CommandSpecDsl.parse(CommandSpecDsl.write(spec));

        assertArrayEquals(new String[] {"Enable verbose output.", "May be repeated."},
                parsed.findOption("--verbose").description());
    }

    @Test
    public void roundTripsMultiLinePositionalDescription() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.addPositional(PositionalParamSpec.builder()
                .paramLabel("<file>")
                .type(File.class)
                .description("The file to read.", "Defaults to stdin.")
                .build());

        CommandSpec parsed = CommandSpecDsl.parse(CommandSpecDsl.write(spec));

        assertArrayEquals(new String[] {"The file to read.", "Defaults to stdin."},
                parsed.positionalParameters().get(0).description());
    }

    @Test
    public void carriageReturnsDoNotSplitDescriptionLines() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.usageMessage().description("first\r\nsecond");

        CommandSpec parsed = CommandSpecDsl.parse(CommandSpecDsl.write(spec));

        assertArrayEquals(new String[] {"first", "second"}, parsed.usageMessage().description());
    }

    @Test
    public void writesMixinStandardHelpOptions() {
        CommandSpec spec = CommandSpec.create().name("app");
        spec.mixinStandardHelpOptions(true);
        spec.addOption(OptionSpec.builder("-v", "--verbose").type(boolean.class).build());

        String dsl = CommandSpecDsl.write(spec);
        assertTrue(dsl.contains("mixinStandardHelpOptions"));
        assertFalse(dsl.contains("option -h, --help"));
        assertFalse(dsl.contains("option -V, --version"));

        CommandSpec roundTripped = CommandSpecDsl.parse(dsl);
        assertTrue(roundTripped.mixinStandardHelpOptions());
        assertNotNull(roundTripped.findOption("--verbose"));
        assertNotNull(roundTripped.findOption("--help"));
        assertNotNull(roundTripped.findOption("--version"));
        assertEquals(3, roundTripped.options().size());
    }
}
