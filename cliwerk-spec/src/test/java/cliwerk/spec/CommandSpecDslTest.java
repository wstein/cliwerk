package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

import java.io.File;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CommandSpecDslTest {

    @Test
    public void parsesNameAndDescription() {
        CommandSpec spec = CommandSpecDsl.parse("command flix \"The Flix programming language\" {}");
        assertEquals("flix", spec.name());
        assertArrayEquals(new String[] {"The Flix programming language"}, spec.usageMessage().description());
    }

    @Test
    public void parsesCommandWithoutDescription() {
        CommandSpec spec = CommandSpecDsl.parse("command flix {}");
        assertEquals("flix", spec.name());
    }

    @Test
    public void picocliConvertsValuesForEveryArgTypesName() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command fetch {\n" +
                "  option --uri : URI\n" +
                "  option --url : URL\n" +
                "  option --ratio : BigDecimal\n" +
                "  option --count : BigInteger\n" +
                "  option --out : Path\n" +
                "}");

        ParseResult result = new CommandLine(spec).parseArgs(
                "--uri=urn:isbn:0451450523", "--url=https://picocli.info/", "--ratio=1.5", "--count=42",
                "--out=build/out.txt");

        assertEquals(java.net.URI.create("urn:isbn:0451450523"), result.matchedOptionValue("--uri", (java.net.URI) null));
        assertEquals("https://picocli.info/", result.matchedOptionValue("--url", (java.net.URL) null).toString());
        assertEquals(new java.math.BigDecimal("1.5"), result.matchedOptionValue("--ratio", (java.math.BigDecimal) null));
        assertEquals(java.math.BigInteger.valueOf(42), result.matchedOptionValue("--count", (java.math.BigInteger) null));
        assertEquals(java.nio.file.Paths.get("build/out.txt"), result.matchedOptionValue("--out", (java.nio.file.Path) null));
    }

    @Test
    public void hiddenAttributeHidesFromDefaultHelpButOptionStillWorks() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option --Xfoo : boolean hidden\n" +
                "  option --bar : boolean\n" +
                "}");

        assertTrue(spec.findOption("--Xfoo").hidden());
        assertFalse(spec.findOption("--bar").hidden());

        CommandLine cmd = new CommandLine(spec);
        ParseResult result = cmd.parseArgs("--Xfoo");
        assertTrue(result.matchedOptionValue("--Xfoo", Boolean.FALSE));
    }

    @Test
    public void hiddenAttributeWorksOnPositionalsToo() {
        CommandSpec spec = CommandSpecDsl.parse("command flix { positional secret : String hidden }");

        assertTrue(spec.positionalParameters().get(0).hidden());
    }

    @Test
    public void inheritAttributeSetsScopeTypeInherit() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option --verbose : boolean inherit\n" +
                "  option --config : String\n" +
                "  command build {}\n" +
                "}");

        assertEquals(picocli.CommandLine.ScopeType.INHERIT, spec.findOption("--verbose").scopeType());
        assertEquals(picocli.CommandLine.ScopeType.LOCAL, spec.findOption("--config").scopeType());
    }

    @Test
    public void inheritedOptionActuallyWorksOnASubcommand() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option --verbose : boolean inherit\n" +
                "  command build {}\n" +
                "}");

        CommandLine cmd = new CommandLine(spec);
        ParseResult result = cmd.parseArgs("build", "--verbose");

        assertTrue(result.hasSubcommand());
        assertTrue(result.subcommand().matchedOptionValue("--verbose", Boolean.FALSE));
    }

    @Test
    public void inheritAttributeWorksOnPositionalsToo() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  positional files : File[] inherit arity=0..*\n" +
                "}");

        assertEquals(picocli.CommandLine.ScopeType.INHERIT, spec.positionalParameters().get(0).scopeType());
    }

    @Test
    public void parsesUsageHelpAndVersionHelpAttributes() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option --help : boolean \"prints usage\" usageHelp\n" +
                "  option --version : boolean versionHelp\n" +
                "  option --json : boolean\n" +
                "}");

        assertTrue(spec.findOption("--help").usageHelp());
        assertFalse(spec.findOption("--help").versionHelp());
        assertTrue(spec.findOption("--version").versionHelp());
        assertFalse(spec.findOption("--json").usageHelp());
    }

    @Test
    public void usageHelpActuallyShortCircuitsExecution() {
        CommandSpec spec = CommandSpecDsl.parse("command flix { option --help : boolean usageHelp }");

        CommandLine cmd = new CommandLine(spec);
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode);
        assertTrue(cmd.getParseResult().isUsageHelpRequested());
    }

    @Test
    public void parsesOptionsWithNamesTypeDescriptionAndAttributes() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option -v, --verbose : boolean \"Enable verbose output\"\n" +
                "  option -o, --output : String \"Output directory\" default=build\n" +
                "  option -t, --target : String required arity=1\n" +
                "}");

        assertEquals(3, spec.options().size());

        OptionSpec verbose = spec.findOption("--verbose");
        assertArrayEquals(new String[] {"-v", "--verbose"}, verbose.names());
        assertEquals(boolean.class, verbose.type());
        assertArrayEquals(new String[] {"Enable verbose output"}, verbose.description());
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
    public void parsesPositionalParams() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  positional files : File \"Input files\" arity=0..*\n" +
                "}");

        assertEquals(1, spec.positionalParameters().size());
        PositionalParamSpec files = spec.positionalParameters().get(0);
        assertEquals("<files>", files.paramLabel());
        assertEquals(File.class, files.type());
        assertArrayEquals(new String[] {"Input files"}, files.description());
        assertEquals("0..*", files.arity().toString());
    }

    /**
     * Regression test: a scalar type (as in {@link #parsesPositionalParams}) declares the right
     * arity/paramLabel but cannot actually collect more than one value -- picocli throws
     * UnmatchedArgumentException on the second value, since a scalar field can only ever hold
     * one match. An array type is required for a multi-value positional to actually work.
     */
    @Test
    public void arrayTypePositionalActuallyCollectsMultipleValues() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  positional files : File[] \"Input files\" arity=0..*\n" +
                "}");

        PositionalParamSpec files = spec.positionalParameters().get(0);
        assertEquals(File[].class, files.type());

        CommandLine cmd = new CommandLine(spec);
        cmd.parseArgs("a.flix", "b.flix", "c.flix");

        assertArrayEquals(new File[] {new File("a.flix"), new File("b.flix"), new File("c.flix")}, (File[]) files.getValue());
    }

    @Test
    public void arrayTypeOptionActuallyCollectsMultipleValues() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option --tag : String[] arity=0..*\n" +
                "}");

        CommandLine cmd = new CommandLine(spec);
        cmd.parseArgs("--tag", "a", "--tag", "b");

        assertArrayEquals(new String[] {"a", "b"}, (String[]) spec.findOption("--tag").getValue());
    }

    /**
     * Not a format feature -- picocli already treats a bare "--" token as end-of-options by
     * default, so everything after it (including option-looking tokens) is captured as
     * positional values, with no special syntax needed in the DSL/JSON. Verified here rather
     * than just documented, since it was flagged as a possible gap before being checked.
     */
    @Test
    public void doubleDashSeparatorPassesRawTokensToAnArrayTypePositional() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option -v, --verbose : boolean\n" +
                "  positional args : String[] arity=0..*\n" +
                "}");

        CommandLine cmd = new CommandLine(spec);
        cmd.parseArgs("-v", "--", "-x", "--foo", "bar");

        boolean verbose = spec.findOption("--verbose").getValue();
        assertTrue(verbose);
        assertArrayEquals(new String[] {"-x", "--foo", "bar"}, (String[]) spec.positionalParameters().get(0).getValue());
    }

    @Test
    public void parsesNestedSubcommands() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  command build \"Compile the project\" {\n" +
                "    option --release : boolean\n" +
                "  }\n" +
                "  command run {\n" +
                "    positional args : String arity=0..*\n" +
                "  }\n" +
                "}");

        assertEquals(2, spec.subcommands().size());
        CommandSpec build = spec.subcommands().get("build").getCommandSpec();
        assertEquals("build", build.name());
        assertArrayEquals(new String[] {"Compile the project"}, build.usageMessage().description());
        assertEquals("--release", build.options().get(0).longestName());

        CommandSpec run = spec.subcommands().get("run").getCommandSpec();
        assertEquals(1, run.positionalParameters().size());
    }

    @Test
    public void builtCommandLineActuallyParsesArguments() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix {\n" +
                "  option -v, --verbose : boolean\n" +
                "  command build {\n" +
                "    option --release : boolean\n" +
                "  }\n" +
                "}");

        CommandLine cmd = new CommandLine(spec);
        ParseResult result = cmd.parseArgs("-v", "build", "--release");

        assertTrue(result.matchedOptionValue("--verbose", Boolean.FALSE));
        assertTrue(result.hasSubcommand());
        assertTrue(result.subcommand().matchedOptionValue("--release", Boolean.FALSE));
    }

    @Test
    public void rejectsUnknownType() {
        try {
            CommandSpecDsl.parse("command flix { option -x : Frobnicator }");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Frobnicator"));
        }
    }

    @Test
    public void skipsLineComments() {
        CommandSpec spec = CommandSpecDsl.parse(
                "// a leading comment\n" +
                "command flix { // trailing comment after the opening brace\n" +
                "  // a comment on its own line\n" +
                "  option -v, --verbose : boolean // trailing comment after a declaration\n" +
                "}\n" +
                "// a trailing comment at the very end");

        assertEquals("flix", spec.name());
        assertEquals(1, spec.options().size());
        assertEquals("--verbose", spec.options().get(0).longestName());
    }

    @Test
    public void lineCommentDoesNotAffectSlashesInsideAString() {
        CommandSpec spec = CommandSpecDsl.parse(
                "command flix \"see https://example.org/docs for more\" {}");

        assertArrayEquals(new String[] {"see https://example.org/docs for more"}, spec.usageMessage().description());
    }

    @Test
    public void rejectsMissingBrace() {
        try {
            CommandSpecDsl.parse("command flix { option -v : boolean");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            // ok
        }
    }

    @Test
    public void rejectsGarbageAtTopLevel() {
        try {
            CommandSpecDsl.parse("not a command");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            // ok
        }
    }

    @Test
    public void parsesMixinStandardHelpOptions() {
        String dsl = "command app {\n" +
                "  mixinStandardHelpOptions\n" +
                "  option -v, --verbose : boolean\n" +
                "}";
        CommandSpec spec = CommandSpecDsl.parse(dsl);
        assertTrue(spec.mixinStandardHelpOptions());
        assertNotNull(spec.findOption("--help"));
        assertNotNull(spec.findOption("--version"));
        assertNotNull(spec.findOption("--verbose"));
        assertEquals(3, spec.options().size());

        CommandLine cmd = new CommandLine(spec);
        assertTrue(cmd.parseArgs("-h").isUsageHelpRequested());
        assertTrue(cmd.parseArgs("-V").isVersionHelpRequested());
    }

    @Test
    public void parsesMixinStandardHelpOptionsOnSubcommand() {
        String dsl = "command app {\n" +
                "  command sub {\n" +
                "    mixinStandardHelpOptions\n" +
                "  }\n" +
                "}";
        CommandSpec spec = CommandSpecDsl.parse(dsl);
        assertFalse(spec.mixinStandardHelpOptions());
        CommandSpec sub = spec.subcommands().get("sub").getCommandSpec();
        assertTrue(sub.mixinStandardHelpOptions());
        assertNotNull(sub.findOption("--help"));
        assertNotNull(sub.findOption("--version"));
    }

    @Test
    public void mixinStandardHelpOptionsConvertsIdenticallyBetweenJsonAndDsl() {
        String dsl = "command app {\n" +
                "  mixinStandardHelpOptions\n" +
                "  option -v, --verbose : boolean\n" +
                "}";
        CommandSpec specFromDsl = CommandSpecDsl.parse(dsl);
        String json = CommandSpecJson.write(specFromDsl);
        CommandSpec specFromJson = CommandSpecJson.read(json);

        assertEquals(specFromDsl.mixinStandardHelpOptions(), specFromJson.mixinStandardHelpOptions());
        assertEquals(specFromDsl.options().size(), specFromJson.options().size());
        assertEquals(CommandSpecDsl.write(specFromDsl), CommandSpecDsl.write(specFromJson));
    }
}
