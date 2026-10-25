package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** JSON equivalents of {@link CommandSpecDslHiddenGroupTest} and {@link CommandSpecDslBundleTest}. */
public class CommandSpecJsonHiddenGroupAndBundleTest {

    // PrintStream(OutputStream, boolean, Charset) and ByteArrayOutputStream#toString(Charset) are
    // Java 10+; this module targets Java 8, so the String-encoding-name overloads are used instead.
    private static String usageOf(CommandSpec spec) throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CommandLine(spec).usage(new PrintStream(out, true, "UTF-8"), CommandLine.Help.Ansi.OFF);
        return out.toString("UTF-8");
    }

    @Test
    public void hiddenArgGroupCreatesNoRealArgGroup() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": false, \"hidden\": true, \"heading\": \"Experimental\", \"options\": [" +
                "  { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" }," +
                "  { \"names\": [\"--Xbar\"], \"type\": \"boolean\" }" +
                "] }" +
                "]}");

        assertTrue(spec.argGroups().isEmpty());
        assertTrue(spec.findOption("--Xfoo").hidden());
        assertTrue(spec.findOption("--Xbar").hidden());
    }

    @Test
    public void hiddenArgGroupProducesNoHeadingAndNoStrayBracket() throws UnsupportedEncodingException {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", " +
                "\"options\": [ { \"names\": [\"--normal\"], \"type\": \"boolean\" } ], " +
                "\"argGroups\": [ { \"exclusive\": false, \"hidden\": true, \"heading\": \"Experimental\", \"options\": [" +
                "  { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" } ] } ]}");

        String usage = usageOf(spec);
        assertFalse(usage.contains("Experimental"));
        assertFalse(usage.contains("[]"));
    }

    @Test
    public void hiddenSubgroupFlattensIntoTheVisibleParentGroup() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": false, \"heading\": \"Output\", \"options\": [ { \"names\": [\"--json\"], \"type\": \"boolean\" } ], " +
                "  \"subgroups\": [ { \"exclusive\": true, \"hidden\": true, \"options\": [" +
                "    { \"names\": [\"--Xa\"], \"type\": \"boolean\" }, { \"names\": [\"--Xb\"], \"type\": \"boolean\" } ] } ]" +
                "}]}");

        assertEquals(1, spec.argGroups().size());
        ArgGroupSpec outer = spec.argGroups().get(0);
        assertTrue(outer.subgroups().isEmpty());
        assertEquals(3, outer.args().size());
        assertTrue(spec.findOption("--Xa").hidden());
        assertFalse(spec.findOption("--json").hidden());
    }

    @Test
    public void definitionsBundlesAndUseExpandOptionsAndPositionals() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--Xfoo\": { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" }, " +
                "                 \"--Xbar\": { \"names\": [\"--Xbar\"], \"type\": \"boolean\" } }," +
                "  \"positionalParams\": { \"files\": { \"paramLabel\": \"<files>\", \"type\": \"File[]\", \"arity\": \"0..*\" } }," +
                "  \"bundles\": { \"xflags\": { \"options\": [\"--Xfoo\", \"--Xbar\"], \"positionalParams\": [\"files\"] } }" +
                "}, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"use\": [\"xflags\"] }" +
                "]}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(2, check.options().size());
        assertTrue(check.findOption("--Xfoo") != null);
        assertTrue(check.findOption("--Xbar") != null);
        assertEquals(1, check.positionalParameters().size());
    }

    @Test
    public void twoUsesOfTheSameBundleGetIndependentInstances() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--Xfoo\": { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": { \"xflags\": { \"options\": [\"--Xfoo\"] } }" +
                "}, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"use\": [\"xflags\"] }," +
                "{ \"name\": \"build\", \"use\": [\"xflags\"] }" +
                "]}");

        assertTrue(spec.subcommands().get("check").getCommandSpec().findOption("--Xfoo")
                != spec.subcommands().get("build").getCommandSpec().findOption("--Xfoo"));
    }

    @Test
    public void useCanAppearOnAnArgGroup() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--Xfoo\": { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": { \"xflags\": { \"options\": [\"--Xfoo\"] } }" +
                "}, \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": false, \"heading\": \"Experimental\", \"use\": [\"xflags\"] }" +
                "]}");

        assertEquals(1, spec.argGroups().size());
        assertEquals(1, spec.argGroups().get(0).args().size());
    }

    @Test
    public void rejectsUseOfUndefinedBundle() {
        try {
            CommandSpecJson.read("{ \"name\": \"flix\", \"use\": [\"nope\"] }");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("nope"));
        }
    }

    @Test
    public void rejectsBundleReferencingUndefinedOption() {
        try {
            CommandSpecJson.read("{ \"definitions\": { \"bundles\": {" +
                    "\"xflags\": { \"options\": [\"--nope\"] } } }, \"name\": \"flix\" }");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("--nope"));
        }
    }

    @Test
    public void bundleCanBundleAGroup() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--json\": { \"names\": [\"--json\"], \"type\": \"boolean\" }, " +
                "                 \"--xml\": { \"names\": [\"--xml\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": { \"outputFormat\": { \"groups\": [" +
                "    { \"exclusive\": true, \"heading\": \"Output format\", \"options\": [\"--json\", \"--xml\"] }" +
                "  ] } }" +
                "}, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"use\": [\"outputFormat\"] }" +
                "]}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(1, check.argGroups().size());
        assertTrue(check.argGroups().get(0).exclusive());
        assertEquals("Output format", check.argGroups().get(0).heading());
        assertEquals(2, check.options().size());
    }

    @Test
    public void twoUsesOfABundleGroupGetIndependentGroupInstances() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--json\": { \"names\": [\"--json\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": { \"outputFormat\": { \"groups\": [ { \"exclusive\": true, \"options\": [\"--json\"] } ] } }" +
                "}, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"use\": [\"outputFormat\"] }," +
                "{ \"name\": \"build\", \"use\": [\"outputFormat\"] }" +
                "]}");

        assertTrue(spec.subcommands().get("check").getCommandSpec().argGroups().get(0)
                != spec.subcommands().get("build").getCommandSpec().argGroups().get(0));
    }

    @Test
    public void bundleGroupCanBeHidden() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--Xfoo\": { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": { \"xflags\": { \"groups\": [" +
                "    { \"exclusive\": false, \"hidden\": true, \"heading\": \"Experimental\", \"options\": [\"--Xfoo\"] }" +
                "  ] } }" +
                "}, \"name\": \"flix\", \"subcommands\": [ { \"name\": \"check\", \"use\": [\"xflags\"] } ]}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertTrue(check.argGroups().isEmpty());
        assertTrue(check.findOption("--Xfoo").hidden());
    }

    @Test
    public void bundleCanUseAnotherBundleAtItsOwnTopLevel() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--github-token\": { \"names\": [\"--github-token\"], \"type\": \"String\" }, " +
                "                 \"--Xfoo\": { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": {" +
                "    \"commonOptions\": { \"options\": [\"--github-token\"] }," +
                "    \"xflags\": { \"options\": [\"--Xfoo\"] }," +
                "    \"everything\": { \"use\": [\"commonOptions\", \"xflags\"] }" +
                "  }" +
                "}, \"name\": \"flix\", \"subcommands\": [ { \"name\": \"check\", \"use\": [\"everything\"] } ]}");

        CommandSpec check = spec.subcommands().get("check").getCommandSpec();
        assertEquals(2, check.options().size());
        assertTrue(check.findOption("--github-token") != null);
        assertTrue(check.findOption("--Xfoo") != null);
    }

    @Test
    public void bundleComposedFromAnotherBundleGetsIndependentInstancesPerUse() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--json\": { \"names\": [\"--json\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": { \"inner\": { \"options\": [\"--json\"] }, \"outer\": { \"use\": [\"inner\"] } }" +
                "}, \"name\": \"flix\", \"subcommands\": [" +
                "{ \"name\": \"check\", \"use\": [\"outer\"] }," +
                "{ \"name\": \"build\", \"use\": [\"outer\"] }" +
                "]}");

        assertTrue(spec.subcommands().get("check").getCommandSpec().findOption("--json")
                != spec.subcommands().get("build").getCommandSpec().findOption("--json"));
    }

    @Test
    public void expandedOptionsActuallyParse() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": {" +
                "  \"options\": { \"--Xfoo\": { \"names\": [\"--Xfoo\"], \"type\": \"boolean\" } }," +
                "  \"bundles\": { \"xflags\": { \"options\": [\"--Xfoo\"] } }" +
                "}, \"name\": \"flix\", \"subcommands\": [ { \"name\": \"check\", \"use\": [\"xflags\"] } ]}");
        CommandLine cmd = new CommandLine(spec);

        CommandLine.ParseResult result = cmd.parseArgs("check", "--Xfoo");

        assertTrue(result.subcommand().matchedOptionValue("--Xfoo", Boolean.FALSE));
    }
}
