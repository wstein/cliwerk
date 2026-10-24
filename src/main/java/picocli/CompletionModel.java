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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Range;

/**
 * Internal, shell-independent view of what a shell completion script generator needs to know about a
 * picocli command hierarchy. {@link AutoComplete}'s generators for individual shells render this model in their
 * own syntax and with their own quoting; nothing in here is specific to any shell.
 * <p>The model is built eagerly by {@link #build(CommandLine)} and is not updated afterwards, but it is not a
 * deep copy either: nodes keep references to the underlying picocli objects and read some values (like
 * descriptions) from them on demand.</p>
 * <p>The model is a <em>tree</em>, not a DAG: a {@code CommandLine} that is registered as a subcommand of two
 * different parents becomes two separate nodes. The root {@link CommandNode} has no {@linkplain CommandNode#names()
 * names}; the caller supplies the name of the script/command.</p>
 * <p>The model already applies the rules that every shell shares:</p>
 * <ul>
 *   <li>hidden subcommands, hidden options and hidden positional parameters are left out;</li>
 *   <li>all names (aliases) under which the same subcommand is registered are grouped into one node;</li>
 *   <li>subcommands, options and positional parameters keep their declaration order.</li>
 * </ul>
 * <p>This class is not part of the public API. Note that {@code AutoComplete.java} is therefore no longer a
 * single self-contained source file: it needs this file as well.</p>
 */
final class CompletionModel {
    private CompletionModel() { }

    /** Builds the model for the specified command and all of its visible subcommands. */
    static CommandNode build(CommandLine commandLine) {
        if (commandLine == null) { throw new NullPointerException("commandLine"); }
        return buildNode(Collections.<String>emptyList(), commandLine);
    }

    private static CommandNode buildNode(List<String> names, CommandLine commandLine) {
        CommandSpec spec = commandLine.getCommandSpec();

        List<OptionNode> options = new ArrayList<OptionNode>();
        for (OptionSpec option : spec.options()) {
            if (option.hidden()) { continue; }
            options.add(new OptionNode(option, spec));
        }
        List<PositionalNode> positionals = new ArrayList<PositionalNode>();
        for (PositionalParamSpec positional : spec.positionalParameters()) {
            if (positional.hidden()) { continue; }
            positionals.add(new PositionalNode(positional));
        }

        // group subcommands by their target CommandLine, so all aliases of the same subcommand end up in one node
        Map<CommandLine, List<String>> children = new LinkedHashMap<CommandLine, List<String>>();
        for (Map.Entry<String, CommandLine> entry : commandLine.getSubcommands().entrySet()) {
            if (entry.getValue().getCommandSpec().usageMessage().hidden()) { continue; }
            List<String> childNames = children.get(entry.getValue());
            if (childNames == null) { children.put(entry.getValue(), childNames = new ArrayList<String>()); }
            childNames.add(entry.getKey());
        }
        List<CommandNode> subcommands = new ArrayList<CommandNode>();
        for (Map.Entry<CommandLine, List<String>> entry : children.entrySet()) {
            subcommands.add(buildNode(entry.getValue(), entry.getKey()));
        }
        return new CommandNode(names, commandLine, options, positionals, subcommands);
    }

    /** A command (the root command or a visible subcommand) in the completion model. */
    static final class CommandNode {
        private final List<String> names;
        private final CommandLine commandLine;
        private final List<OptionNode> options;
        private final List<PositionalNode> positionals;
        private final List<CommandNode> subcommands;

        CommandNode(List<String> names, CommandLine commandLine, List<OptionNode> options,
                    List<PositionalNode> positionals, List<CommandNode> subcommands) {
            this.names = Collections.unmodifiableList(new ArrayList<String>(names));
            this.commandLine = commandLine;
            this.options = Collections.unmodifiableList(options);
            this.positionals = Collections.unmodifiableList(positionals);
            this.subcommands = Collections.unmodifiableList(subcommands);
        }

        /**
         * The names under which the parent command knows this subcommand: the registered name first, followed by
         * its aliases, in registration order. Empty for the root command, whose name is chosen by the caller.
         */
        List<String> names() { return names; }

        /** The underlying {@code CommandLine}. */
        CommandLine commandLine() { return commandLine; }

        /** The underlying {@code CommandSpec}. */
        CommandSpec spec() { return commandLine.getCommandSpec(); }

        /** The raw usage help description lines of this command (may contain markup and format specifiers). */
        String[] description() { return spec().usageMessage().description(); }

        /** The visible options of this command, in declaration order. */
        List<OptionNode> options() { return options; }

        /** The visible positional parameters of this command, in declaration order. */
        List<PositionalNode> positionals() { return positionals; }

        /** The visible subcommands of this command, in registration order, with aliases grouped. */
        List<CommandNode> subcommands() { return subcommands; }

        /** The names and aliases of all visible subcommands, in the order of {@link #subcommands()}. */
        List<String> allSubcommandNames() {
            List<String> result = new ArrayList<String>();
            for (CommandNode child : subcommands) { result.addAll(child.names()); }
            return result;
        }
    }

    /** Common properties of options and positional parameters. */
    abstract static class ArgNode {
        private final ArgSpec spec;
        private final Candidates candidates;

        ArgNode(ArgSpec spec) {
            this.spec = spec;
            this.candidates = Candidates.of(spec);
        }

        /** The underlying {@code ArgSpec}. */
        ArgSpec spec() { return spec; }

        /** Whether this is an option (as opposed to a positional parameter). */
        abstract boolean isOption();

        /** The arity of this option or positional parameter. */
        Range arity() { return spec.arity(); }

        /** Whether this option or positional parameter consumes at least one value. */
        boolean takesValue() { return spec.arity().max() > 0; }

        /** Whether this option or positional parameter can take a value, but does not require one (like arity {@code 0..1}). */
        boolean valueOptional() { return spec.arity().min() == 0 && spec.arity().max() > 0; }

        /** The completion candidates for the value(s), or {@code null} if none are known. */
        Candidates candidates() { return candidates; }

        /** The raw usage help description lines (may contain markup and format specifiers). */
        String[] description() { return spec.description(); }
    }

    /** A visible option. */
    static final class OptionNode extends ArgNode {
        private final List<String> names;
        private final List<String> negatedNames;

        OptionNode(OptionSpec spec, CommandSpec command) {
            super(spec);
            this.names = Collections.unmodifiableList(Arrays.asList(spec.names()));
            this.negatedNames = Collections.unmodifiableList(negatedNames(spec, command));
        }

        /**
         * Derives the negative forms of a negatable option the same way {@code CommandSpec} registers them: each
         * name is passed to the command's {@code negatableOptionTransformer}, and a result equal to the name itself
         * means that name has no negative form. A {@code null} result is not a valid option name and is skipped too.
         */
        private static List<String> negatedNames(OptionSpec option, CommandSpec command) {
            List<String> result = new ArrayList<String>();
            if (!option.negatable()) { return result; }
            for (String name : option.names()) {
                String negated = command.negatableOptionTransformer().makeNegative(name, command);
                if (negated != null && !name.equals(negated)) { result.add(negated); }
            }
            return result;
        }

        boolean isOption() { return true; }

        OptionSpec spec() { return (OptionSpec) super.spec(); }

        /**
         * All names of this option, in declaration order, exactly as declared. This may include names that a
         * particular shell cannot express (like {@code /?} or {@code +v}); it is up to each generator to skip those.
         */
        List<String> names() { return names; }

        /**
         * The negative names of this option (like {@code --no-verbose} for {@code --verbose}), in the order of
         * {@link #names()}; empty if the option is not negatable. Like {@code names()}, these are not filtered for
         * what a particular shell can express.
         */
        List<String> negatedNames() { return negatedNames; }
    }

    /** A visible positional parameter. */
    static final class PositionalNode extends ArgNode {
        PositionalNode(PositionalParamSpec spec) { super(spec); }

        boolean isOption() { return false; }

        PositionalParamSpec spec() { return (PositionalParamSpec) super.spec(); }

        /** The index range of this positional parameter. */
        Range index() { return spec().index(); }
    }

    /** The kinds of completion candidates. */
    enum CandidateKind {
        /** A fixed list of literal values (from {@code completionCandidates}, including enum constants). */
        VALUES,
        /** Host names; each shell supplies them with its own mechanism. */
        HOSTNAMES
    }

    /** The completion candidates for the value of an option or positional parameter. */
    static final class Candidates {
        private final CandidateKind kind;
        private final List<String> values;

        private Candidates(CandidateKind kind, List<String> values) {
            this.kind = kind;
            this.values = Collections.unmodifiableList(values);
        }

        /**
         * Returns the candidates for the specified option or positional parameter, or {@code null} if none are known.
         * Explicit {@code completionCandidates} (which picocli also sets for enum types) take precedence;
         * otherwise an {@code InetAddress} type (or element type, for multi-value types) yields {@link CandidateKind#HOSTNAMES}.
         */
        static Candidates of(ArgSpec arg) {
            if (arg.completionCandidates() != null) {
                List<String> values = new ArrayList<String>();
                for (String value : arg.completionCandidates()) { values.add(value); }
                return new Candidates(CandidateKind.VALUES, values);
            }
            Class<?> type = arg.typeInfo().isMultiValue() ? arg.typeInfo().getAuxiliaryTypes()[0] : arg.type();
            if (type.equals(InetAddress.class)) {
                return new Candidates(CandidateKind.HOSTNAMES, Collections.<String>emptyList());
            }
            return null;
        }

        CandidateKind kind() { return kind; }

        /** The literal candidate values for {@link CandidateKind#VALUES}; empty for other kinds. */
        List<String> values() { return values; }
    }
}
