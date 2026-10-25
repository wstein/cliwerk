/*
   Copyright 2017 Remko Popma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package picocli;

import org.junit.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CompletionModel.CandidateKind;
import picocli.CompletionModel.CommandNode;
import picocli.CompletionModel.OptionNode;
import picocli.CompletionModel.PositionalNode;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Tests the shell-independent completion model that the shell completion generators of {@link AutoComplete} use. */
public class CompletionModelTest {

    enum Color { RED, GREEN, BLUE }

    static class Special extends ArrayList<String> {
        Special() { super(Arrays.asList("plain", "with space", "it's", "$(touch /tmp/x)", "a\\b")); }
    }

    @Command(name = "top", description = {"Top @|bold level|@", "second line"})
    static class Top {
        @Option(names = {"-v", "--verbose"}, description = "be verbose") boolean verbose;
        @Option(names = "--secret", hidden = true) String secret;
        @Option(names = {"-classpath", "/cp", "+c"}) String classpath;
        @Option(names = "--unit") TimeUnit unit;
        @Option(names = "--colors", split = ",") List<Color> colors;
        @Option(names = "--pick", completionCandidates = Special.class) String pick;
        @Option(names = "--host") InetAddress host;
        @Option(names = "--hosts") List<InetAddress> hosts;
        @Option(names = "--file") File file;
        @Option(names = "--opt", arity = "0..1") String optional;
        @Parameters(index = "0", description = "the color") Color color;
        @Parameters(index = "1", hidden = true) String hiddenPositional;
        @Parameters(index = "2..*") InetAddress[] rest;
    }

    @Command(name = "first", aliases = {"f", "1st"}, description = "first sub") static class First { }
    @Command(name = "hidden", aliases = "h", hidden = true) static class Hidden { }
    @Command static class Plain { }
    @Command(name = "second") static class Second {
        @Option(names = "--x") int x;
    }

    private static CommandNode model() {
        CommandLine cmd = new CommandLine(new Top())
                .addSubcommand(new CommandLine(new First()).addSubcommand(new Hidden()).addSubcommand(new Second()))
                .addSubcommand(new Hidden())
                .addSubcommand(new Second())
                .addSubcommand("zeta", new Plain(), "z")
                .addSubcommand("alpha", new Second());
        return CompletionModel.build(cmd);
    }

    private static OptionNode option(CommandNode node, String name) {
        for (OptionNode option : node.options()) {
            if (option.names().contains(name)) { return option; }
        }
        return null;
    }

    private static List<String> namesOf(List<CommandNode> nodes) {
        List<String> result = new ArrayList<String>();
        for (CommandNode node : nodes) { result.add(node.names().get(0)); }
        return result;
    }

    @Test(expected = NullPointerException.class)
    public void buildRejectsNull() {
        CompletionModel.build(null);
    }

    @Test
    public void rootHasNoNamesAndKeepsItsCommandLine() {
        CommandLine cmd = new CommandLine(new Top());
        CommandNode root = CompletionModel.build(cmd);
        assertTrue(root.names().isEmpty());
        assertSame(cmd, root.commandLine());
        assertSame(cmd.getCommandSpec(), root.spec());
        assertArrayEquals(new String[] {"Top @|bold level|@", "second line"}, root.description());
    }

    @Test
    public void hiddenSubcommandsAreFilteredAtEveryLevel() {
        CommandNode root = model();
        assertEquals(Arrays.asList("first", "second", "zeta", "alpha"), namesOf(root.subcommands()));
        assertFalse(root.allSubcommandNames().contains("hidden"));
        assertFalse(root.allSubcommandNames().contains("h"));
        CommandNode first = root.subcommands().get(0);
        assertEquals(Arrays.asList("second"), namesOf(first.subcommands()));
    }

    @Test
    public void hiddenOptionsAndPositionalsAreFiltered() {
        CommandNode root = model();
        assertNull(option(root, "--secret"));
        assertEquals(2, root.positionals().size());
        for (PositionalNode positional : root.positionals()) {
            assertFalse(positional.spec().hidden());
        }
        assertEquals(0, root.positionals().get(0).index().min());
        assertEquals(2, root.positionals().get(1).index().min());
    }

    @Test
    public void aliasesOfOneSubcommandAreGroupedInRegistrationOrder() {
        CommandNode root = model();
        assertEquals(Arrays.asList("first", "f", "1st"), root.subcommands().get(0).names());
        assertEquals(Arrays.asList("zeta", "z"), root.subcommands().get(2).names());
        assertEquals(Arrays.asList("first", "f", "1st", "second", "zeta", "z", "alpha"), root.allSubcommandNames());
    }

    @Test
    public void namesUnderWhichTheSameCommandLineIsRegisteredAreGrouped() {
        CommandLine shared = new CommandLine(new Second());
        CommandLine parent = new CommandLine(CommandSpec.create());
        parent.addSubcommand("one", shared);
        parent.addSubcommand("other", new Second());
        parent.addSubcommand("two", shared);
        CommandNode root = CompletionModel.build(parent);
        assertEquals(2, root.subcommands().size());
        assertEquals(Arrays.asList("one", "two"), root.subcommands().get(0).names());
        assertEquals(Arrays.asList("other"), root.subcommands().get(1).names());
    }

    @Test
    public void subcommandOrderIsRegistrationOrderNotAlphabetical() {
        assertEquals(Arrays.asList("first", "second", "zeta", "alpha"), namesOf(model().subcommands()));
    }

    @Test
    public void subcommandNodesExposeTheirOwnModel() {
        CommandNode second = model().subcommands().get(1);
        assertEquals(1, second.options().size());
        assertEquals(Arrays.asList("--x"), second.options().get(0).names());
        assertTrue(second.subcommands().isEmpty());
        assertTrue(second.allSubcommandNames().isEmpty());
        assertArrayEquals(new String[] {"first sub"}, model().subcommands().get(0).description());
    }

    @Test
    public void optionsKeepDeclarationOrder() {
        List<String> first = new ArrayList<String>();
        for (OptionNode option : model().options()) { first.add(option.names().get(0)); }
        assertEquals(Arrays.asList("-v", "-classpath", "--unit", "--colors", "--pick", "--host", "--hosts", "--file", "--opt"), first);
    }

    @Test
    public void optionNamesAreKeptAsDeclaredIncludingNamesSomeShellsCannotExpress() {
        CommandNode root = model();
        assertEquals(Arrays.asList("-v", "--verbose"), option(root, "-v").names());
        assertEquals(Arrays.asList("-classpath", "/cp", "+c"), option(root, "-classpath").names());
        assertTrue(option(root, "-v").isOption());
        assertFalse(root.positionals().get(0).isOption());
    }

    @Test
    public void optionArityAndValue() {
        CommandNode root = model();
        assertFalse(option(root, "-v").takesValue());
        assertEquals(0, option(root, "-v").arity().max());
        assertTrue(option(root, "--file").takesValue());
        assertTrue(option(root, "--opt").takesValue());
        assertEquals(0, option(root, "--opt").arity().min());
        assertEquals(1, option(root, "--opt").arity().max());
    }

    @Test
    public void descriptionsAreRawUsageHelpLines() {
        CommandNode root = model();
        assertArrayEquals(new String[] {"be verbose"}, option(root, "-v").description());
        assertArrayEquals(new String[] {"the color"}, root.positionals().get(0).description());
        assertEquals(0, option(root, "--file").description().length);
    }

    @Test
    public void noCandidatesForPlainTypes() {
        CommandNode root = model();
        assertNull(option(root, "-v").candidates());
        assertNull(option(root, "--file").candidates());
        assertNull(option(root, "-classpath").candidates());
    }

    @Test
    public void enumValuesAreValueCandidates() {
        CommandNode root = model();
        CompletionModel.Candidates unit = option(root, "--unit").candidates();
        assertEquals(CandidateKind.VALUES, unit.kind());
        List<String> expected = new ArrayList<String>();
        for (TimeUnit u : TimeUnit.values()) { expected.add(u.name()); }
        assertEquals(expected, unit.values());

        assertEquals(Arrays.asList("RED", "GREEN", "BLUE"), option(root, "--colors").candidates().values());
        assertEquals(CandidateKind.VALUES, root.positionals().get(0).candidates().kind());
        assertEquals(Arrays.asList("RED", "GREEN", "BLUE"), root.positionals().get(0).candidates().values());
    }

    @Test
    public void completionCandidatesAreKeptVerbatimWithoutShellQuoting() {
        CompletionModel.Candidates pick = option(model(), "--pick").candidates();
        assertEquals(CandidateKind.VALUES, pick.kind());
        assertEquals(Arrays.asList("plain", "with space", "it's", "$(touch /tmp/x)", "a\\b"), pick.values());
    }

    @Test
    public void inetAddressYieldsHostnamesKind() {
        CommandNode root = model();
        assertEquals(CandidateKind.HOSTNAMES, option(root, "--host").candidates().kind());
        assertTrue(option(root, "--host").candidates().values().isEmpty());
        assertEquals(CandidateKind.HOSTNAMES, option(root, "--hosts").candidates().kind()); // List<InetAddress>
        assertEquals(CandidateKind.HOSTNAMES, root.positionals().get(1).candidates().kind()); // InetAddress[]
    }

    @Test
    public void explicitCandidatesTakePrecedenceOverInetAddress() {
        CommandSpec spec = CommandSpec.create();
        spec.addOption(OptionSpec.builder("--host").type(InetAddress.class)
                .completionCandidates(Arrays.asList("localhost", "example.org")).build());
        CommandNode root = CompletionModel.build(new CommandLine(spec));
        assertEquals(CandidateKind.VALUES, root.options().get(0).candidates().kind());
        assertEquals(Arrays.asList("localhost", "example.org"), root.options().get(0).candidates().values());
    }

    @Test
    public void programmaticSpecIsSupported() {
        CommandSpec spec = CommandSpec.create();
        spec.addOption(OptionSpec.builder("-x").type(int.class).build());
        spec.addOption(OptionSpec.builder("--hid").hidden(true).type(int.class).build());
        spec.addPositional(PositionalParamSpec.builder().type(String.class).build());
        CommandSpec child = CommandSpec.create().aliases("c1", "c2");
        spec.addSubcommand("child", child);
        CommandSpec hidden = CommandSpec.create();
        hidden.usageMessage().hidden(true);
        spec.addSubcommand("hc", hidden);

        CommandNode root = CompletionModel.build(new CommandLine(spec));
        assertEquals(1, root.options().size());
        assertEquals(1, root.positionals().size());
        assertNull(root.positionals().get(0).candidates());
        assertEquals(1, root.subcommands().size());
        assertEquals(Arrays.asList("child", "c1", "c2"), root.subcommands().get(0).names());
    }

    @Test
    public void modelListsAreUnmodifiable() {
        CommandNode root = model();
        assertUnmodifiable(root.names());
        assertUnmodifiable(root.options());
        assertUnmodifiable(root.positionals());
        assertUnmodifiable(root.subcommands());
        assertUnmodifiable(root.subcommands().get(0).names());
        assertUnmodifiable(option(root, "-v").names());
        assertUnmodifiable(option(root, "--unit").candidates().values());
    }

    @Command(name = "neg")
    static class Negatable {
        @Option(names = {"-v", "--no-verbose"}, negatable = true) boolean verbose;
        @Option(names = "-c", negatable = true) boolean c;
        @Option(names = "-XX:+Inline", negatable = true) boolean inline;
        @Option(names = "--plain") boolean plain;
        @Option(names = "--hidden-neg", negatable = true, hidden = true) boolean hiddenNeg;
    }

    @Test
    public void negatedNamesUseTheDefaultTransformer() {
        CommandNode root = CompletionModel.build(new CommandLine(new Negatable()));
        assertEquals(Arrays.asList("--verbose"), option(root, "-v").negatedNames()); // -v has no negative form
        assertEquals(Collections.<String>emptyList(), option(root, "-c").negatedNames());
        assertEquals(Arrays.asList("-XX:-Inline"), option(root, "-XX:+Inline").negatedNames());
        assertEquals(Collections.<String>emptyList(), option(root, "--plain").negatedNames()); // not negatable
        assertNull(option(root, "--hidden-neg")); // hidden negatable options are filtered as before
        assertUnmodifiable(option(root, "-v").negatedNames());
    }

    @Test
    public void longestName() {
        CommandNode root = CompletionModel.build(new CommandLine(new Negatable()));
        assertEquals("--no-verbose", option(root, "-v").longestName());
        assertEquals("-XX:+Inline", option(root, "-XX:+Inline").longestName());
        assertEquals("--verbose", option(model(), "-v").longestName());
    }

    @Test
    public void negatedNamesMatchWhatTheParserAccepts() {
        CommandLine cmd = new CommandLine(new Negatable());
        CommandNode root = CompletionModel.build(cmd);
        List<String> modelNames = new ArrayList<String>();
        for (OptionNode option : root.options()) { modelNames.addAll(option.negatedNames()); }
        List<String> specNames = new ArrayList<String>(cmd.getCommandSpec().negatedOptionsMap().keySet());
        specNames.remove("--hidden-neg".replace("--", "--no-")); // hidden options are not in the model
        Collections.sort(modelNames);
        Collections.sort(specNames);
        assertEquals(specNames, modelNames);
    }

    @Test
    public void negatedNamesUseACustomTransformer() {
        CommandLine cmd = new CommandLine(new Negatable());
        cmd.setNegatableOptionTransformer(new CommandLine.INegatableOptionTransformer() {
            public String makeNegative(String optionName, CommandSpec spec) {
                return optionName.startsWith("--") ? "--not-" + optionName.substring(2) : optionName;
            }
            public String makeSynopsis(String optionName, CommandSpec spec) { return optionName; }
        });
        CommandNode root = CompletionModel.build(cmd);
        assertEquals(Arrays.asList("--not-no-verbose"), option(root, "-v").negatedNames());
        assertEquals(Collections.<String>emptyList(), option(root, "-c").negatedNames());
        assertEquals(Collections.<String>emptyList(), option(root, "-XX:+Inline").negatedNames());
    }

    @Test
    public void valueOptionalOnlyForArityWithZeroMinimumAndPositiveMaximum() {
        CommandSpec spec = CommandSpec.create();
        spec.addOption(OptionSpec.builder("--a0").arity("0").build());
        spec.addOption(OptionSpec.builder("--a1").arity("1").type(String.class).build());
        spec.addOption(OptionSpec.builder("--a01").arity("0..1").type(String.class).build());
        spec.addOption(OptionSpec.builder("--a0n").arity("0..*").type(String[].class).build());
        spec.addPositional(PositionalParamSpec.builder().arity("0..1").type(String.class).build());
        CommandNode root = CompletionModel.build(new CommandLine(spec));
        assertFalse(option(root, "--a0").valueOptional());
        assertFalse(option(root, "--a1").valueOptional());
        assertTrue(option(root, "--a01").valueOptional());
        assertTrue(option(root, "--a0n").valueOptional());
        assertTrue(root.positionals().get(0).valueOptional());
    }

    @Command(name = "desc", description = {"Be @|bold verbose|@ and 100%% sure.%nSecond line.", "next line"})
    static class Descriptions {
        @Option(names = "--markup", description = "  @|red,bold red|@ text  ") boolean markup;
        @Option(names = "--bad", description = "bad %s format %q") boolean bad;
        @Option(names = "--none") boolean none;
        @Option(names = "--blank", description = "  ") boolean blank;
        @Parameters(description = "it's a 'quoted' \\ value") String p;
    }

    @Test
    public void plainDescriptionStripsMarkupResolvesFormatAndKeepsFirstLine() {
        CommandNode root = CompletionModel.build(new CommandLine(new Descriptions()));
        assertEquals("Be verbose and 100% sure.", root.plainDescription());
        assertEquals("red text", option(root, "--markup").plainDescription());
        assertEquals("bad %s format %q", option(root, "--bad").plainDescription()); // invalid format string kept as is
        assertEquals("", option(root, "--none").plainDescription());
        assertEquals("", option(root, "--blank").plainDescription());
        assertEquals("it's a 'quoted' \\ value", root.positionals().get(0).plainDescription()); // no shell quoting
    }

    @Test
    public void plainTextHandlesEmptyAndNullDescriptions() {
        assertEquals("", CompletionModel.plainText(new String[0]));
        assertEquals("", CompletionModel.plainText(null));
        assertEquals("a", CompletionModel.plainText(new String[] {"a%nb", "c"}));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertUnmodifiable(List list) {
        try {
            list.add(null);
            fail("expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // ---- what the PowerShell generator relies on: it walks the typed words down this model ----

    @Test
    public void positionalIndexRangesAreExposedWithUnboundedMaximum() {
        CommandNode root = CompletionModel.build(new CommandLine(new Top()));
        PositionalNode first = root.positionals().get(0);
        assertEquals(0, first.index().min());
        assertEquals(0, first.index().max());
        PositionalNode rest = root.positionals().get(1);
        assertEquals(2, rest.index().min());
        assertEquals(Integer.MAX_VALUE, rest.index().max());
    }

    @Command(name = "inh", subcommands = InheritedChild.class)
    static class InheritedRoot {
        @Option(names = "--shared", scope = CommandLine.ScopeType.INHERIT) boolean shared;
        @Option(names = "--rootonly") boolean rootOnly;
        @Option(names = "--many", arity = "1..*") List<String> many;
    }
    @Command(name = "child") static class InheritedChild {
        @Option(names = "--own") boolean own;
    }

    @Test
    public void inheritedOptionsAreListedInTheSubcommandNode() {
        CommandNode root = CompletionModel.build(new CommandLine(new InheritedRoot()));
        CommandNode child = root.subcommands().get(0);
        assertNotNull(option(child, "--shared"));
        assertNotNull(option(child, "--own"));
        assertNull(option(child, "--rootonly"));
    }

    @Test
    public void unboundedArityHasPositiveMinimumAndHugeMaximum() {
        OptionNode many = option(CompletionModel.build(new CommandLine(new InheritedRoot())), "--many");
        assertEquals(1, many.arity().min());
        assertTrue(many.arity().max() > 1000);
        assertTrue(many.takesValue());
        assertFalse(many.valueOptional());
    }
}
