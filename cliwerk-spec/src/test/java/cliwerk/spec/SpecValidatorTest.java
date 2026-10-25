package cliwerk.spec;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards against a picocli parsing ambiguity discovered the hard way while building the flix
 * example (see cliwerk-spec/examples/README.md): an option's defaultValue that's textually
 * identical to a sibling subcommand's name is rejected by picocli when it applies that default
 * -- unconditionally, on every invocation, even with zero arguments given (confirmed
 * empirically). Not a picocli bug -- the parser being conservative about ambiguous input -- but
 * better caught here than at first use.
 */
public class SpecValidatorTest {

    @Test
    public void rejectsDefaultValueCollidingWithASubcommandName() {
        CommandSpec spec = CommandSpec.create().name("flix");
        spec.addOption(OptionSpec.builder("--output").type(String.class).defaultValue("build").build());
        spec.addSubcommand("build", CommandSpec.create().name("build"));

        try {
            SpecValidator.validate(spec);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("--output"));
            assertTrue(expected.getMessage().contains("build"));
        }
    }

    @Test
    public void acceptsANonCollidingDefaultValue() {
        CommandSpec spec = CommandSpec.create().name("flix");
        spec.addOption(OptionSpec.builder("--output").type(String.class).defaultValue("out").build());
        spec.addSubcommand("build", CommandSpec.create().name("build"));

        SpecValidator.validate(spec); // no exception
    }

    @Test
    public void checksNestedSubcommandsToo() {
        CommandSpec spec = CommandSpec.create().name("flix");
        CommandSpec build = CommandSpec.create().name("build");
        build.addOption(OptionSpec.builder("--mode").type(String.class).defaultValue("classes").build());
        build.addSubcommand("classes", CommandSpec.create().name("classes"));
        spec.addSubcommand("build", build);

        try {
            SpecValidator.validate(spec);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("classes"));
        }
    }

    @Test
    public void jsonReadRejectsTheCollidingShape() {
        try {
            CommandSpecJson.read("{ \"name\": \"flix\", \"options\": [" +
                    "{ \"names\": [\"--output\"], \"type\": \"String\", \"defaultValue\": \"build\" } ]," +
                    "\"subcommands\": [ { \"name\": \"build\" } ] }");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("build"));
        }
    }

    @Test
    public void dslParseRejectsTheCollidingShape() {
        try {
            CommandSpecDsl.parse("command flix {\n" +
                    "  option --output : String default=build\n" +
                    "  command build {}\n" +
                    "}");
            fail("expected DslParseException");
        } catch (DslParseException expected) {
            assertTrue(expected.getMessage().contains("build"));
        }
    }

    @Test
    public void mergeRejectsACollisionIntroducedByMerging() {
        CommandSpec uber = CommandSpec.create().name("uber");
        uber.addOption(OptionSpec.builder("--tool").type(String.class).defaultValue("flix").build());
        CommandSpec flix = CommandSpec.create().name("flix");

        try {
            CommandSpecMerger.merge(uber, flix);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("flix"));
        }
    }
}
