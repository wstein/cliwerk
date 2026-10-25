package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A {@code bundle} inside {@code definitions} names a reusable bundle of already-defined
 * options/positionals; {@code use <name>} inside a command or group body expands the whole
 * bundle at that point (each member freshly cloned, same as an individual reference) instead of
 * listing every member name individually.
 */
public class CommandSpecDslBundleTest {

    @Test
    public void useExpandsEveryOptionInTheBundle() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --Xfoo : boolean\n" +
                "  option --Xbar : boolean\n" +
                "  bundle xflags {\n" +
                "    option --Xfoo\n" +
                "    option --Xbar\n" +
                "  }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check {\n" +
                "    use xflags\n" +
                "  }\n" +
                "}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(2, check.options().size());
        assertTrue(check.findOption("--Xfoo") != null);
        assertTrue(check.findOption("--Xbar") != null);
    }

    @Test
    public void useExpandsPositionalsToo() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  positional files : File[] arity=0..*\n" +
                "  bundle inputs {\n" +
                "    positional files\n" +
                "  }\n" +
                "}\n" +
                "command flix {\n" +
                "  command build {\n" +
                "    use inputs\n" +
                "  }\n" +
                "}");

        CommandSpec build = spec.subcommands().get("build").getCommandSpec();
        assertEquals(1, build.positionalParameters().size());
    }

    @Test
    public void useCanBeCombinedWithOtherStatementsInTheSameCommand() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --Xfoo : boolean\n" +
                "  bundle xflags { option --Xfoo }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check {\n" +
                "    option --explain : boolean\n" +
                "    use xflags\n" +
                "    option --json : boolean\n" +
                "  }\n" +
                "}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(3, check.options().size());
    }

    @Test
    public void useCanAppearInsideAGroup() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --Xfoo : boolean\n" +
                "  bundle xflags { option --Xfoo }\n" +
                "}\n" +
                "command flix {\n" +
                "  group cooperative \"Experimental\" {\n" +
                "    use xflags\n" +
                "  }\n" +
                "}");

        assertEquals(1, spec.argGroups().size());
        assertEquals(1, spec.argGroups().get(0).args().size());
    }

    @Test
    public void twoUsesOfTheSameBundleGetIndependentInstances() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --Xfoo : boolean\n" +
                "  bundle xflags { option --Xfoo }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check { use xflags }\n" +
                "  command build { use xflags }\n" +
                "}");

        assertNotSame(
                spec.subcommands().get("check").getCommandSpec().findOption("--Xfoo"),
                spec.subcommands().get("build").getCommandSpec().findOption("--Xfoo"));
    }

    @Test
    public void bundleMemberMustAlreadyBeDefined() {
        try {
            CommandSpecDsl.parse("definitions { bundle xflags { option --nope } }\ncommand flix {}");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            assertTrue(expected.getMessage().contains("--nope"));
        }
    }

    @Test
    public void rejectsUseOfUndefinedBundle() {
        try {
            CommandSpecDsl.parse("command flix { use nope }");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            assertTrue(expected.getMessage().contains("nope"));
        }
    }

    @Test
    public void bundleCanBundleAGroup() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --json : boolean\n" +
                "  option --xml : boolean\n" +
                "  bundle outputFormat {\n" +
                "    group exclusive \"Output format\" {\n" +
                "      option --json\n" +
                "      option --xml\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check { use outputFormat }\n" +
                "}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(1, check.argGroups().size());
        assertTrue(check.argGroups().get(0).exclusive());
        assertEquals("Output format", check.argGroups().get(0).heading());
        assertEquals(2, check.options().size());
    }

    @Test
    public void twoUsesOfABundleGroupGetIndependentGroupInstances() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --json : boolean\n" +
                "  bundle outputFormat {\n" +
                "    group exclusive { option --json }\n" +
                "  }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check { use outputFormat }\n" +
                "  command build { use outputFormat }\n" +
                "}");

        assertNotSame(
                spec.subcommands().get("check").getCommandSpec().argGroups().get(0),
                spec.subcommands().get("build").getCommandSpec().argGroups().get(0));
        assertNotSame(
                spec.subcommands().get("check").getCommandSpec().findOption("--json"),
                spec.subcommands().get("build").getCommandSpec().findOption("--json"));
    }

    @Test
    public void bundleGroupCanBeExclusiveGroupWithMutualExclusionEnforced() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --json : boolean\n" +
                "  option --xml : boolean\n" +
                "  bundle outputFormat {\n" +
                "    group exclusive { option --json option --xml }\n" +
                "  }\n" +
                "}\n" +
                "command flix { command check { use outputFormat } }");
        CommandLine cmd = new CommandLine(spec);

        try {
            cmd.parseArgs("check", "--json", "--xml");
            fail("expected MutuallyExclusiveArgsException");
        } catch (CommandLine.MutuallyExclusiveArgsException expected) {
            // ok
        }
    }

    @Test
    public void bundleGroupCanBeHidden() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --Xfoo : boolean\n" +
                "  bundle xflags {\n" +
                "    group cooperative hidden \"Experimental\" { option --Xfoo }\n" +
                "  }\n" +
                "}\n" +
                "command flix { command check { use xflags } }");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertTrue(check.argGroups().isEmpty());
        assertTrue(check.findOption("--Xfoo").hidden());
    }

    @Test
    public void bundleCanUseAnotherBundleAtItsOwnTopLevel() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --github-token : String\n" +
                "  option --no-install : boolean\n" +
                "  option --threads : int\n" +
                "  bundle commonOptions {\n" +
                "    option --github-token\n" +
                "    option --no-install\n" +
                "    option --threads\n" +
                "  }\n" +
                "  option --Xfoo : boolean\n" +
                "  bundle xflags {\n" +
                "    option --Xfoo\n" +
                "  }\n" +
                "  bundle everything {\n" +
                "    use commonOptions\n" +
                "    use xflags\n" +
                "  }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check { use everything }\n" +
                "}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(4, check.options().size());
        assertTrue(check.findOption("--github-token") != null);
        assertTrue(check.findOption("--no-install") != null);
        assertTrue(check.findOption("--threads") != null);
        assertTrue(check.findOption("--Xfoo") != null);
    }

    @Test
    public void bundleComposedFromAnotherBundleGetsIndependentInstancesPerUse() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --json : boolean\n" +
                "  bundle inner { option --json }\n" +
                "  bundle outer { use inner }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check { use outer }\n" +
                "  command build { use outer }\n" +
                "}");

        assertNotSame(
                spec.subcommands().get("check").getCommandSpec().findOption("--json"),
                spec.subcommands().get("build").getCommandSpec().findOption("--json"));
    }

    @Test
    public void expandedOptionsActuallyParse() {
        CommandSpec spec = CommandSpecDsl.parse(
                "definitions {\n" +
                "  option --Xfoo : boolean\n" +
                "  bundle xflags { option --Xfoo }\n" +
                "}\n" +
                "command flix {\n" +
                "  command check { use xflags }\n" +
                "}");
        CommandLine cmd = new CommandLine(spec);

        CommandLine.ParseResult result = cmd.parseArgs("check", "--Xfoo");

        assertTrue(result.subcommand().matchedOptionValue("--Xfoo", Boolean.FALSE));
    }
}
