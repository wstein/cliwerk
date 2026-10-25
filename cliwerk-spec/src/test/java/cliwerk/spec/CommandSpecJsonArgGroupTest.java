package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.MutuallyExclusiveArgsException;
import picocli.CommandLine.ParseResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** JSON equivalent of {@link CommandSpecDslArgGroupTest}: a document-root "argGroups" array. */
public class CommandSpecJsonArgGroupTest {

    @Test
    public void readsExclusiveGroupAndExposesItsOptionsOnTheCommand() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": true, \"options\": [" +
                "  { \"names\": [\"--json\"], \"type\": \"boolean\" }," +
                "  { \"names\": [\"--xml\"], \"type\": \"boolean\" }" +
                "] }" +
                "]}");

        assertEquals(2, spec.options().size());
        assertEquals(1, spec.argGroups().size());
        assertTrue(spec.argGroups().get(0).exclusive());
    }

    @Test
    public void exclusiveGroupRejectsBothOptionsTogether() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": true, \"options\": [" +
                "  { \"names\": [\"--json\"], \"type\": \"boolean\" }," +
                "  { \"names\": [\"--xml\"], \"type\": \"boolean\" }" +
                "] }" +
                "]}");
        CommandLine cmd = new CommandLine(spec);

        try {
            cmd.parseArgs("--json", "--xml");
            fail("expected MutuallyExclusiveArgsException");
        } catch (MutuallyExclusiveArgsException expected) {
            // ok
        }
    }

    @Test
    public void readsMultiplicityAndHeading() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": true, \"multiplicity\": \"1\", \"heading\": \"Output format\", \"options\": [" +
                "  { \"names\": [\"--json\"], \"type\": \"boolean\" }" +
                "] }" +
                "]}");

        ArgGroupSpec group = spec.argGroups().get(0);
        assertEquals("1", group.multiplicity().toString());
        assertEquals("Output format", group.heading());
    }

    @Test
    public void readsPositionalParamsAndSubgroups() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": false, " +
                "  \"positionalParams\": [ { \"paramLabel\": \"<files>\", \"type\": \"File[]\", \"arity\": \"0..*\" } ], " +
                "  \"subgroups\": [ { \"exclusive\": true, \"options\": [" +
                "    { \"names\": [\"--a\"], \"type\": \"boolean\" }, { \"names\": [\"--b\"], \"type\": \"boolean\" } ] } ]" +
                "}]}");

        assertEquals(1, spec.positionalParameters().size());
        ArgGroupSpec outer = spec.argGroups().get(0);
        assertEquals(1, outer.subgroups().size());
        assertEquals(2, spec.options().size());
    }

    @Test
    public void optionsAndPositionalParamsMayBeDefinitionReferences() {
        CommandSpec spec = CommandSpecJson.read("{ \"definitions\": { \"options\": {" +
                "\"--json\": { \"names\": [\"--json\"], \"type\": \"boolean\" }" +
                "} }, \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": true, \"options\": [ \"--json\", { \"names\": [\"--xml\"], \"type\": \"boolean\" } ] }" +
                "]}");

        assertTrue(spec.findOption("--json") != null);
        assertTrue(spec.findOption("--xml") != null);
    }

    @Test
    public void writingAndReadingArgGroupsRoundTrips() {
        CommandSpec spec = CommandSpecJson.read("{ \"name\": \"flix\", \"argGroups\": [" +
                "{ \"exclusive\": true, \"multiplicity\": \"1\", \"heading\": \"Output format\", \"options\": [" +
                "  { \"names\": [\"--json\"], \"type\": \"boolean\" }," +
                "  { \"names\": [\"--xml\"], \"type\": \"boolean\" }" +
                "] }" +
                "]}");

        String json = CommandSpecJson.write(spec);
        CommandSpec roundTripped = CommandSpecJson.read(json);

        assertEquals(1, roundTripped.argGroups().size());
        ArgGroupSpec group = roundTripped.argGroups().get(0);
        assertTrue(group.exclusive());
        assertEquals("1", group.multiplicity().toString());
        assertEquals("Output format", group.heading());
        assertEquals(2, roundTripped.options().size());
    }
}
