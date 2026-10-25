package cliwerk.spec;

import picocli.CommandLine.Help;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.HelpSectionSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A lightweight, human-friendly text format for describing a picocli {@link CommandSpec},
 * as an alternative to hand-writing {@link CommandSpecJson JSON}. Compiling the DSL always
 * produces the same {@code CommandSpec} model that {@link CommandSpecJson} reads and writes,
 * so a spec can be authored in this DSL and exported to JSON for other tooling, or merged
 * with other specs via {@link CommandSpecMerger}.
 *
 * <h2>Grammar (informal)</h2>
 * <pre>
 * [ definitions {
 *     ( option &lt;name&gt; (, &lt;name&gt;)* : &lt;type&gt; ["&lt;description&gt;"] [default=&lt;value&gt;] [required] [arity=&lt;range&gt;]
 *     | positional &lt;label&gt; : &lt;type&gt; ["&lt;description&gt;"] [default=&lt;value&gt;] [required] [arity=&lt;range&gt;]
 *     )*
 * } ]
 * command &lt;name&gt; ["&lt;description&gt;"] {
 *     ( option &lt;name&gt; (, &lt;name&gt;)* ( : &lt;type&gt; ["&lt;description&gt;"] [default=&lt;value&gt;] [required] [arity=&lt;range&gt;] )?
 *     | positional &lt;label&gt; ( : &lt;type&gt; ["&lt;description&gt;"] [default=&lt;value&gt;] [required] [arity=&lt;range&gt;] )?
 *     | command ... (nested, same grammar)
 *     )*
 * }
 * </pre>
 * <p>A positional parameter's bare {@code &lt;label&gt;} is wrapped as {@code "&lt;label&gt;"} to
 * form its {@linkplain PositionalParamSpec#paramLabel() paramLabel}, matching picocli's own
 * convention for annotated fields. Supported {@code &lt;type&gt;} names are the same as
 * {@link CommandSpecJson}'s (currently scalar types only). A {@code //} starts a line comment,
 * running to end of line; it is not recognized inside a quoted string.</p>
 * <p>A {@code "&lt;description&gt;"} may span several help lines: each {@code \n} inside it starts a
 * new element of the resulting {@code String[]} description, and {@link #write(CommandSpec)} joins
 * multi-element descriptions back with {@code \n}, so descriptions survive a round trip unchanged.</p>
 * <p>An {@code option}/{@code positional} statement inside a {@code command} body that omits the
 * {@code : <type>} part (and everything after it) is a <em>reference</em> to a same-named option
 * or positional param declared once in the optional top-level {@code definitions} block, instead
 * of a new definition -- the DSL's answer to many commands sharing the same option, without
 * repeating its type/description/etc. verbatim in each one. A definition and a reference can be
 * freely mixed within one command's own option/positional list. An option reference must name
 * exactly one option (the one being referenced); a multi-name list only makes sense when
 * defining (with a {@code :}), not referencing.</p>
 * <p>Example:</p>
 * <pre>
 * definitions {
 *   option --release : boolean "Optimize for release"
 * }
 * command flix "The Flix programming language" {
 *   option -v, --verbose : boolean "Enable verbose output"
 *   command build "Compile the project" {
 *     option --release
 *   }
 * }
 * </pre>
 */
public final class CommandSpecDsl {

    private CommandSpecDsl() {}

    /** Parses the given DSL text into a {@link CommandSpec} (with any nested subcommands). */
    public static CommandSpec parse(String dsl) {
        List<Token> tokens = new Lexer(dsl).tokenize();
        Parser parser = new Parser(tokens);
        Definitions definitions = parser.parseDefinitionsBlockIfPresent();
        CommandSpec spec = parser.parseCommand(definitions);
        parser.expectEnd();
        try {
            SpecValidator.validate(spec);
        } catch (IllegalArgumentException ambiguity) {
            throw new DslParseException(ambiguity.getMessage());
        }
        return spec;
    }

    /** Serializes the given {@link CommandSpec} (with any nested subcommands) to DSL text. */
    public static String write(CommandSpec spec) {
        StringBuilder out = new StringBuilder();
        writeCommand(spec, out, 0);
        return out.toString();
    }

    private static void writeCommand(CommandSpec spec, StringBuilder out, int indent) {
        indent(out, indent);
        out.append("command ");
        String name = spec.name();
        if (name == null || name.isEmpty() || "<main class>".equals(name)) {
            name = "command";
        }
        out.append(name);
        String helpSection = Help.getHelpSection(spec);
        if (helpSection != null && !helpSection.isEmpty()) {
            out.append(" helpSection=").append(quote(helpSection));
        }
        String desc = joinDescription(spec.usageMessage().description());
        if (desc != null && !desc.isEmpty()) {
            out.append(" ").append(quote(desc));
        }
        out.append(" {\n");

        Set<OptionSpec> standardHelpOptions = Collections.emptySet();
        if (spec.mixinStandardHelpOptions()) {
            indent(out, indent + 1);
            out.append("mixinStandardHelpOptions\n");
            CommandSpec helpMixin = spec.mixins().get("mixinStandardHelpOptions");
            if (helpMixin != null) {
                standardHelpOptions = new HashSet<OptionSpec>(helpMixin.options());
            }
        }

        for (OptionSpec option : spec.options()) {
            if (option.group() == null && !option.inherited() && !standardHelpOptions.contains(option)) {
                writeOption(option, out, indent + 1);
            }
        }
        for (PositionalParamSpec positional : spec.positionalParameters()) {
            if (positional.group() == null && !positional.inherited()) {
                writePositional(positional, out, indent + 1);
            }
        }
        for (ArgGroupSpec group : spec.argGroups()) {
            writeGroup(group, out, indent + 1);
        }
        for (HelpSectionSpec sec : spec.helpSectionSpecs().values()) {
            writeSection(sec, out, indent + 1);
        }
        for (picocli.CommandLine sub : spec.subcommands().values()) {
            writeCommand(sub.getCommandSpec(), out, indent + 1);
        }

        indent(out, indent);
        out.append("}\n");
    }

    private static void writeOption(OptionSpec option, StringBuilder out, int indent) {
        indent(out, indent);
        out.append("option ");
        String[] names = option.names();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) { out.append(", "); }
            out.append(names[i]);
        }
        out.append(" : ").append(ArgTypes.toName(option.type()));
        String desc = joinDescription(option.description());
        if (desc != null && !desc.isEmpty()) {
            out.append(" ").append(quote(desc));
        }
        if (option.defaultValue() != null) {
            out.append(" default=").append(formatValue(option.defaultValue()));
        }
        if (option.required()) {
            out.append(" required");
        }
        if (!isDefaultArity(option)) {
            out.append(" arity=").append(option.arity().toString());
        }
        if (option.scopeType() == picocli.CommandLine.ScopeType.INHERIT) {
            out.append(" inherit");
        }
        if (option.hidden()) {
            out.append(" hidden");
        }
        String helpSection = Help.getHelpSection(option);
        if (helpSection != null) {
            out.append(" helpSection=").append(quote(helpSection));
        } else if (option.usageHelp()) {
            out.append(" usageHelp");
        }
        if (option.versionHelp()) {
            out.append(" versionHelp");
        }
        out.append("\n");
    }

    private static void writePositional(PositionalParamSpec positional, StringBuilder out, int indent) {
        indent(out, indent);
        out.append("positional ");
        out.append(unwrapParamLabel(positional.paramLabel()));
        out.append(" : ").append(ArgTypes.toName(positional.type()));
        String desc = joinDescription(positional.description());
        if (desc != null && !desc.isEmpty()) {
            out.append(" ").append(quote(desc));
        }
        if (positional.defaultValue() != null) {
            out.append(" default=").append(formatValue(positional.defaultValue()));
        }
        if (positional.required()) {
            out.append(" required");
        }
        if (!isDefaultArity(positional)) {
            out.append(" arity=").append(positional.arity().toString());
        }
        if (positional.scopeType() == picocli.CommandLine.ScopeType.INHERIT) {
            out.append(" inherit");
        }
        if (positional.hidden()) {
            out.append(" hidden");
        }
        String helpSection = Help.getHelpSection(positional);
        if (helpSection != null && !helpSection.isEmpty()) {
            out.append(" helpSection=").append(quote(helpSection));
        }
        out.append("\n");
    }

    private static void writeSection(HelpSectionSpec sec, StringBuilder out, int indent) {
        indent(out, indent);
        out.append("section ").append(sec.name());
        String desc = joinDescription(sec.description());
        if (desc != null && !desc.isEmpty()) {
            out.append(" ").append(quote(desc));
        }
        if (sec.heading() != null && !sec.heading().isEmpty()) {
            out.append(" heading=").append(quote(sec.heading()));
        }
        if (sec.emptyMessage() != null && !sec.emptyMessage().isEmpty()) {
            out.append(" emptyMessage=").append(quote(sec.emptyMessage()));
        }
        if (sec.notice() != null && !sec.notice().isEmpty()) {
            out.append(" notice=").append(quote(sec.notice()));
        }
        out.append("\n");
    }

    private static void writeGroup(ArgGroupSpec group, StringBuilder out, int indent) {
        indent(out, indent);
        out.append("group ");
        out.append(group.exclusive() ? "exclusive" : "cooperative");
        if (group.multiplicity() != null && !"0..1".equals(group.multiplicity().toString())) {
            out.append(" multiplicity=").append(group.multiplicity().toString());
        }
        String helpSection = Help.getHelpSection(group);
        if (helpSection != null) {
            out.append(" helpSection=").append(quote(helpSection));
        }
        if (group.heading() != null && !group.heading().isEmpty()) {
            out.append(" ").append(quote(group.heading()));
        }
        out.append(" {\n");
        for (picocli.CommandLine.Model.ArgSpec arg : group.args()) {
            if (arg.isOption()) {
                writeOption((OptionSpec) arg, out, indent + 1);
            } else if (arg.isPositional()) {
                writePositional((PositionalParamSpec) arg, out, indent + 1);
            }
        }
        for (ArgGroupSpec subgroup : group.subgroups()) {
            writeGroup(subgroup, out, indent + 1);
        }
        indent(out, indent);
        out.append("}\n");
    }

    private static void indent(StringBuilder out, int indent) {
        for (int i = 0; i < indent; i++) {
            out.append("  ");
        }
    }

    private static String joinDescription(String[] desc) {
        if (desc == null || desc.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < desc.length; i++) {
            if (i > 0) { sb.append("\n"); }
            sb.append(desc[i]);
        }
        return sb.toString();
    }

    /** Inverse of {@link #joinDescription(String[])}: each {@code \n} in the DSL text starts a new description line. */
    private static String[] splitDescription(String desc) {
        List<String> lines = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i <= desc.length(); i++) {
            if (i == desc.length() || desc.charAt(i) == '\n') {
                String line = desc.substring(start, i);
                if (line.endsWith("\r")) { line = line.substring(0, line.length() - 1); }
                lines.add(line);
                start = i + 1;
            }
        }
        return lines.toArray(new String[0]);
    }

    private static String quote(String s) {
        return "\"" + escapeString(s) + "\"";
    }

    private static String escapeString(String s) {
        if (s == null) { return ""; }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': break;
                default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static boolean isSimpleWord(String s) {
        if (s == null || s.isEmpty()) { return false; }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || "{}:,=\"".indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static String formatValue(String value) {
        return isSimpleWord(value) ? value : quote(value);
    }

    private static boolean isDefaultArity(picocli.CommandLine.Model.ArgSpec arg) {
        if (arg.arity() == null) { return true; }
        String s = arg.arity().toString();
        if (arg.isOption()) {
            if (arg.type() == boolean.class || arg.type() == Boolean.class) {
                return "0".equals(s);
            } else {
                return "1".equals(s);
            }
        } else {
            return "1".equals(s);
        }
    }

    static String unwrapParamLabel(String paramLabel) {
        if (paramLabel == null || paramLabel.isEmpty()) {
            return "arg";
        }
        return paramLabel.startsWith("<") && paramLabel.endsWith(">")
                ? paramLabel.substring(1, paramLabel.length() - 1)
                : paramLabel;
    }


    /**
     * The optional top-level {@code definitions} block: named option/positional-param templates
     * that a command body can reference by name (an {@code option}/{@code positional} statement
     * with no {@code :}) instead of repeating the full definition. Options are keyed by every
     * name they were declared with; positional params are keyed by their bare (unwrapped) label.
     * Resolving a reference clones the stored definition via picocli's own
     * {@code OptionSpec.builder(original)}/{@code PositionalParamSpec.builder(original)}, since
     * a single {@code ArgSpec} instance can't belong to more than one {@code CommandSpec}.
     */
    private static final class Definitions {
        static final Definitions EMPTY = new Definitions(
                Collections.<String, OptionSpec>emptyMap(), Collections.<String, PositionalParamSpec>emptyMap(),
                Collections.<String, Bundle>emptyMap());

        final Map<String, OptionSpec> options;
        final Map<String, PositionalParamSpec> positionalParams;
        final Map<String, Bundle> bundles;

        Definitions(Map<String, OptionSpec> options, Map<String, PositionalParamSpec> positionalParams,
                    Map<String, Bundle> bundles) {
            this.options = options;
            this.positionalParams = positionalParams;
            this.bundles = bundles;
        }

        OptionSpec resolveOption(String name) {
            OptionSpec def = options.get(name);
            if (def == null) {
                throw new DslParseException("Reference to undefined option \"" + name + "\": not declared in the definitions block");
            }
            return OptionSpec.builder(def).build();
        }

        PositionalParamSpec resolvePositional(String label) {
            PositionalParamSpec def = positionalParams.get(label);
            if (def == null) {
                throw new DslParseException("Reference to undefined positional parameter \"" + label + "\": not declared in the definitions block");
            }
            return PositionalParamSpec.builder(def).build();
        }

        Bundle resolveBundle(String name) {
            Bundle bundle = bundles.get(name);
            if (bundle == null) {
                throw new DslParseException("Reference to undefined bundle \"" + name + "\": not declared in the definitions block");
            }
            return bundle;
        }
    }

    /**
     * A named, reusable bundle of already-defined option/positional names, plus any groups
     * declared inline within the {@code bundle} block, expanded by a {@code use} statement.
     */
    private static final class Bundle {
        final List<String> optionNames;
        final List<String> positionalLabels;
        final List<GroupTemplate> groups;

        Bundle(List<String> optionNames, List<String> positionalLabels, List<GroupTemplate> groups) {
            this.optionNames = optionNames;
            this.positionalLabels = positionalLabels;
            this.groups = groups;
        }
    }

    /**
     * A group declared inside a {@code bundle} block: unlike a normal inline group (built and
     * attached immediately by {@code parseGroup}), this one may be materialized more than once --
     * once per {@code use} of the bundle -- so its members are stored as templates and cloned
     * fresh (via picocli's own {@code OptionSpec.builder(original)}/{@code PositionalParamSpec.builder(original)})
     * at each {@link #materialize}. Hidden handling mirrors {@code parseGroup}'s exactly.
     */
    private static final class GroupTemplate {
        final boolean exclusive;
        final String multiplicity;
        final boolean hidden;
        final String helpSection;
        final String heading;
        final List<OptionSpec> options;
        final List<PositionalParamSpec> positionals;
        final List<GroupTemplate> subgroups;

        GroupTemplate(boolean exclusive, String multiplicity, boolean hidden, String helpSection, String heading,
                      List<OptionSpec> options, List<PositionalParamSpec> positionals, List<GroupTemplate> subgroups) {
            this.exclusive = exclusive;
            this.multiplicity = multiplicity;
            this.hidden = hidden;
            this.helpSection = helpSection;
            this.heading = heading;
            this.options = options;
            this.positionals = positionals;
            this.subgroups = subgroups;
        }

        void materialize(ArgSink sink) {
            if (hidden) {
                ArgSink hidingSink = new HidingArgSink(sink);
                for (OptionSpec option : options) { hidingSink.addOption(option); }
                for (PositionalParamSpec positional : positionals) { hidingSink.addPositional(positional); }
                for (GroupTemplate subgroup : subgroups) { subgroup.materialize(hidingSink); }
                return;
            }
            ArgGroupSpec.Builder builder = ArgGroupSpec.builder().exclusive(exclusive);
            if (multiplicity != null) { builder.multiplicity(multiplicity); }
            if (helpSection != null) {
                builder.helpSection(helpSection);
            }
            if (heading != null) { builder.heading(heading); }
            ArgSink groupSink = new GroupArgSink(builder);
            for (OptionSpec option : options) { groupSink.addOption(OptionSpec.builder(option).build()); }
            for (PositionalParamSpec positional : positionals) { groupSink.addPositional(PositionalParamSpec.builder(positional).build()); }
            for (GroupTemplate subgroup : subgroups) { subgroup.materialize(groupSink); }
            sink.addGroup(builder.build());
        }
    }

    /**
     * Where a parsed option/positional/group ends up: either a {@link CommandSpec} (a command
     * body) or an {@link ArgGroupSpec.Builder} (a group body). Lets {@code parseGroup} and
     * {@code use}-expansion be written once and used in both contexts.
     */
    private interface ArgSink {
        void addOption(OptionSpec option);
        void addPositional(PositionalParamSpec positional);
        void addGroup(ArgGroupSpec group);
    }

    private static final class CommandArgSink implements ArgSink {
        private final CommandSpec spec;
        CommandArgSink(CommandSpec spec) { this.spec = spec; }
        public void addOption(OptionSpec option) { spec.addOption(option); }
        public void addPositional(PositionalParamSpec positional) { spec.addPositional(positional); }
        public void addGroup(ArgGroupSpec group) { spec.addArgGroup(group); }
    }

    private static final class GroupArgSink implements ArgSink {
        private final ArgGroupSpec.Builder builder;
        GroupArgSink(ArgGroupSpec.Builder builder) { this.builder = builder; }
        public void addOption(OptionSpec option) { builder.addArg(option); }
        public void addPositional(PositionalParamSpec positional) { builder.addArg(positional); }
        public void addGroup(ArgGroupSpec group) { builder.addSubgroup(group); }
    }

    /**
     * Wraps another {@link ArgSink}, forcing every option/positional added through it to
     * {@code hidden}. A nested (non-hidden-declared) subgroup reaching {@link #addGroup} is
     * flattened too, recursively -- once any ancestor group is hidden, nothing group-shaped
     * may reach picocli, so a subgroup's own exclusive/cooperative/multiplicity is discarded
     * rather than preserved as a real nested {@link ArgGroupSpec}.
     */
    private static final class HidingArgSink implements ArgSink {
        private final ArgSink delegate;
        HidingArgSink(ArgSink delegate) { this.delegate = delegate; }
        public void addOption(OptionSpec option) { delegate.addOption(OptionSpec.builder(option).hidden(true).build()); }
        public void addPositional(PositionalParamSpec positional) { delegate.addPositional(PositionalParamSpec.builder(positional).hidden(true).build()); }
        public void addGroup(ArgGroupSpec group) {
            for (picocli.CommandLine.Model.ArgSpec arg : group.args()) {
                if (arg.isOption()) { addOption((OptionSpec) arg); } else { addPositional((PositionalParamSpec) arg); }
            }
            for (ArgGroupSpec subgroup : group.subgroups()) { addGroup(subgroup); }
        }
    }

    // ---- lexer ----

    private enum TokenKind { LBRACE, RBRACE, COLON, COMMA, EQUALS, STRING, WORD, EOF }

    private static final class Token {
        final TokenKind kind;
        final String text;
        Token(TokenKind kind, String text) { this.kind = kind; this.text = text; }
    }

    private static final class Lexer {
        private final String text;
        private int pos;

        Lexer(String text) { this.text = text; }

        List<Token> tokenize() {
            List<Token> tokens = new ArrayList<Token>();
            Token token;
            do {
                token = next();
                tokens.add(token);
            } while (token.kind != TokenKind.EOF);
            return tokens;
        }

        private Token next() {
            skipInsignificant();
            if (pos >= text.length()) { return new Token(TokenKind.EOF, ""); }
            char c = text.charAt(pos);
            switch (c) {
                case '{': pos++; return new Token(TokenKind.LBRACE, "{");
                case '}': pos++; return new Token(TokenKind.RBRACE, "}");
                case ':': pos++; return new Token(TokenKind.COLON, ":");
                case ',': pos++; return new Token(TokenKind.COMMA, ",");
                case '=': pos++; return new Token(TokenKind.EQUALS, "=");
                case '"': return readString();
                default:  return readWord();
            }
        }

        /** Skips whitespace and {@code //} line comments (to end of line), repeating until neither remains. */
        private void skipInsignificant() {
            while (true) {
                while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) { pos++; }
                if (pos + 1 < text.length() && text.charAt(pos) == '/' && text.charAt(pos + 1) == '/') {
                    while (pos < text.length() && text.charAt(pos) != '\n') { pos++; }
                } else {
                    break;
                }
            }
        }

        private Token readString() {
            int start = pos;
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw new DslParseException("Unterminated string starting at position " + start);
                }
                char c = text.charAt(pos++);
                if (c == '"') { break; }
                if (c == '\\' && pos < text.length()) {
                    char escaped = text.charAt(pos++);
                    switch (escaped) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'n':  sb.append('\n'); break;
                        case 't':  sb.append('\t'); break;
                        default:   sb.append(escaped);
                    }
                } else {
                    sb.append(c);
                }
            }
            return new Token(TokenKind.STRING, sb.toString());
        }

        private Token readWord() {
            int start = pos;
            while (pos < text.length() && !Character.isWhitespace(text.charAt(pos)) && "{}:,=\"".indexOf(text.charAt(pos)) < 0) {
                pos++;
            }
            return new Token(TokenKind.WORD, text.substring(start, pos));
        }
    }

    // ---- parser ----

    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) { this.tokens = tokens; }

        private Token current() { return tokens.get(pos); }
        private boolean check(TokenKind kind) { return current().kind == kind; }
        private boolean checkWord(String text) { return check(TokenKind.WORD) && current().text.equals(text); }
        private Token advance() { Token t = current(); if (t.kind != TokenKind.EOF) { pos++; } return t; }

        void expectEnd() {
            if (!check(TokenKind.EOF)) {
                throw new DslParseException("Unexpected trailing content: '" + current().text + "'");
            }
        }

        private void expect(TokenKind kind, String expectedDescription) {
            if (!check(kind)) {
                throw new DslParseException("Expected " + expectedDescription + " but found '" + current().text + "'");
            }
            advance();
        }

        private void expectKeyword(String keyword) {
            if (!checkWord(keyword)) {
                throw new DslParseException("Expected '" + keyword + "' but found '" + current().text + "'");
            }
            advance();
        }

        private String expectWord() {
            if (!check(TokenKind.WORD)) {
                throw new DslParseException("Expected a name but found '" + current().text + "'");
            }
            return advance().text;
        }

        private String expectWordOrString() {
            if (check(TokenKind.WORD) || check(TokenKind.STRING)) { return advance().text; }
            throw new DslParseException("Expected a value but found '" + current().text + "'");
        }

        /** Parses the optional top-level {@code definitions { ... } } block, or returns {@link Definitions#EMPTY} if absent. */
        Definitions parseDefinitionsBlockIfPresent() {
            if (!checkWord("definitions")) { return Definitions.EMPTY; }
            advance();
            expect(TokenKind.LBRACE, "'{'");
            Map<String, OptionSpec> options = new LinkedHashMap<String, OptionSpec>();
            Map<String, PositionalParamSpec> positionalParams = new LinkedHashMap<String, PositionalParamSpec>();
            Map<String, Bundle> bundles = new LinkedHashMap<String, Bundle>();
            while (!check(TokenKind.RBRACE)) {
                if (checkWord("option")) {
                    OptionSpec option = parseOption(Definitions.EMPTY);
                    for (String optionName : option.names()) { options.put(optionName, option); }
                } else if (checkWord("positional")) {
                    PositionalParamSpec positional = parsePositional(Definitions.EMPTY);
                    positionalParams.put(unwrapParamLabel(positional.paramLabel()), positional);
                } else if (checkWord("bundle")) {
                    // Definitions available so far -- a bundle may only include options/positionals
                    // already defined earlier in this same block, by bare reference (no ':').
                    Definitions soFar = new Definitions(options, positionalParams, bundles);
                    advance();
                    String bundleName = expectWord();
                    expect(TokenKind.LBRACE, "'{'");
                    List<String> optionNames = new ArrayList<String>();
                    List<String> positionalLabels = new ArrayList<String>();
                    List<GroupTemplate> groupTemplates = new ArrayList<GroupTemplate>();
                    while (!check(TokenKind.RBRACE)) {
                        if (checkWord("option")) {
                            advance();
                            String name = expectWord();
                            if (check(TokenKind.COLON)) {
                                throw new DslParseException("Bundle members must be bare references to already-defined options (no ':'); found a definition for \"" + name + "\"");
                            }
                            soFar.resolveOption(name); // validates existence; discarded, re-resolved fresh at each "use"
                            optionNames.add(name);
                        } else if (checkWord("positional")) {
                            advance();
                            String label = expectWord();
                            if (check(TokenKind.COLON)) {
                                throw new DslParseException("Bundle members must be bare references to already-defined positional parameters (no ':'); found a definition for \"" + label + "\"");
                            }
                            soFar.resolvePositional(label);
                            positionalLabels.add(label);
                        } else if (checkWord("group")) {
                            groupTemplates.add(parseGroupTemplate(soFar));
                        } else if (checkWord("use")) {
                            advance();
                            String usedBundleName = expectWord();
                            Bundle used = soFar.resolveBundle(usedBundleName);
                            optionNames.addAll(used.optionNames);
                            positionalLabels.addAll(used.positionalLabels);
                            groupTemplates.addAll(used.groups);
                        } else {
                            throw new DslParseException("Expected 'option', 'positional', 'group', or 'use' but found '" + current().text + "'");
                        }
                    }
                    expect(TokenKind.RBRACE, "'}'");
                    bundles.put(bundleName, new Bundle(optionNames, positionalLabels, groupTemplates));
                } else {
                    throw new DslParseException("Expected 'option', 'positional', or 'bundle' but found '" + current().text + "'");
                }
            }
            expect(TokenKind.RBRACE, "'}'");
            return new Definitions(options, positionalParams, bundles);
        }

        CommandSpec parseCommand(Definitions definitions) {
            expectKeyword("command");
            String name = expectWord();
            CommandSpec spec = CommandSpec.create().name(name);
            while (checkWord("helpSection")) {
                advance();
                expect(TokenKind.EQUALS, "'='");
                spec.helpSection(expectWordOrString());
            }
            if (check(TokenKind.STRING)) {
                spec.usageMessage().description(splitDescription(advance().text));
            }
            expect(TokenKind.LBRACE, "'{'");
            ArgSink sink = new CommandArgSink(spec);
            boolean mixinStandardHelp = false;
            while (!check(TokenKind.RBRACE)) {
                if (!check(TokenKind.WORD)) {
                    throw new DslParseException("Expected 'option', 'positional', 'group', 'use', 'mixinStandardHelpOptions', or 'command' but found '" + current().text + "'");
                }
                String keyword = current().text;
                if ("command".equals(keyword)) {
                    CommandSpec sub = parseCommand(definitions);
                    spec.addSubcommand(sub.name(), sub);
                } else if ("section".equals(keyword)) {
                    spec.addHelpSectionSpec(parseSection());
                } else if ("mixinStandardHelpOptions".equals(keyword)) {
                    advance();
                    mixinStandardHelp = true;
                } else if (isMemberKeyword(keyword)) {
                    parseMember(definitions, sink);
                } else {
                    throw new DslParseException("Expected 'option', 'positional', 'group', 'section', 'use', 'mixinStandardHelpOptions', or 'command' but found '" + keyword + "'");
                }
            }
            expect(TokenKind.RBRACE, "'}'");
            if (mixinStandardHelp) {
                spec.mixinStandardHelpOptions(true);
            }
            return spec;
        }

        private boolean isMemberKeyword(String keyword) {
            return "option".equals(keyword) || "positional".equals(keyword) || "group".equals(keyword) || "use".equals(keyword);
        }

        /** Parses one {@code option}/{@code positional}/{@code group}/{@code use} statement into {@code sink}. */
        private void parseMember(Definitions definitions, ArgSink sink) {
            String keyword = current().text;
            if ("option".equals(keyword)) {
                sink.addOption(parseOption(definitions));
            } else if ("positional".equals(keyword)) {
                sink.addPositional(parsePositional(definitions));
            } else if ("group".equals(keyword)) {
                parseGroup(definitions, sink);
            } else if ("use".equals(keyword)) {
                expandBundle(definitions, sink);
            } else {
                throw new DslParseException("Expected 'option', 'positional', 'group', or 'use' but found '" + keyword + "'");
            }
        }

        /** {@code use <name>}: expands every member of a {@code definitions}-block {@code bundle} into {@code sink}, each freshly resolved/cloned. */
        private void expandBundle(Definitions definitions, ArgSink sink) {
            expectKeyword("use");
            String name = expectWord();
            Bundle bundle = definitions.resolveBundle(name);
            for (String optionName : bundle.optionNames) {
                sink.addOption(definitions.resolveOption(optionName));
            }
            for (String label : bundle.positionalLabels) {
                sink.addPositional(definitions.resolvePositional(label));
            }
            for (GroupTemplate groupTemplate : bundle.groups) {
                groupTemplate.materialize(sink);
            }
        }

        private HelpSectionSpec parseSection() {
            expectKeyword("section");
            String name = expectWordOrString();
            HelpSectionSpec.Builder builder = HelpSectionSpec.builder(name);
            if (check(TokenKind.STRING)) {
                builder.description(splitDescription(advance().text));
            }
            while (checkWord("heading") || checkWord("emptyMessage") || checkWord("notice")) {
                String attr = advance().text;
                expect(TokenKind.EQUALS, "'='");
                String value = expectWordOrString();
                if ("heading".equals(attr)) {
                    builder.heading(value);
                } else if ("emptyMessage".equals(attr)) {
                    builder.emptyMessage(value);
                } else if ("notice".equals(attr)) {
                    builder.notice(value);
                }
            }
            return builder.build();
        }

        /**
         * Parses a {@code group} declared inside a {@code bundle} block into a
         * {@link GroupTemplate} instead of materializing it immediately, since a bundle's
         * group may be materialized more than once (once per {@code use}). Grammar is otherwise
         * identical to a normal inline group.
         */
        private GroupTemplate parseGroupTemplate(Definitions definitions) {
            expectKeyword("group");
            String kind = expectWord();
            boolean exclusive;
            if ("exclusive".equals(kind)) {
                exclusive = true;
            } else if ("cooperative".equals(kind)) {
                exclusive = false;
            } else {
                throw new DslParseException("Expected 'exclusive' or 'cooperative' but found '" + kind + "'");
            }

            String multiplicity = null;
            boolean hidden = false;
            String helpSection = null;
            while (checkWord("multiplicity") || checkWord("hidden") || checkWord("helpSection")) {
                String attr = advance().text;
                if ("hidden".equals(attr)) {
                    hidden = true;
                } else if ("helpSection".equals(attr)) {
                    expect(TokenKind.EQUALS, "'='");
                    helpSection = expectWordOrString();
                } else {
                    expect(TokenKind.EQUALS, "'='");
                    multiplicity = expectWordOrString();
                }
            }
            String heading = check(TokenKind.STRING) ? advance().text : null;

            List<OptionSpec> options = new ArrayList<OptionSpec>();
            List<PositionalParamSpec> positionals = new ArrayList<PositionalParamSpec>();
            List<GroupTemplate> subgroups = new ArrayList<GroupTemplate>();
            expect(TokenKind.LBRACE, "'{'");
            while (!check(TokenKind.RBRACE)) {
                if (!check(TokenKind.WORD) || !isMemberKeyword(current().text)) {
                    throw new DslParseException("Expected 'option', 'positional', 'group', or 'use' but found '" + current().text + "'");
                }
                String keyword = current().text;
                if ("option".equals(keyword)) {
                    options.add(parseOption(definitions));
                } else if ("positional".equals(keyword)) {
                    positionals.add(parsePositional(definitions));
                } else if ("group".equals(keyword)) {
                    subgroups.add(parseGroupTemplate(definitions));
                } else if ("use".equals(keyword)) {
                    advance();
                    String name = expectWord();
                    Bundle nested = definitions.resolveBundle(name);
                    for (String optionName : nested.optionNames) { options.add(definitions.resolveOption(optionName)); }
                    for (String label : nested.positionalLabels) { positionals.add(definitions.resolvePositional(label)); }
                    subgroups.addAll(nested.groups);
                }
            }
            expect(TokenKind.RBRACE, "'}'");
            return new GroupTemplate(exclusive, multiplicity, hidden, helpSection, heading, options, positionals, subgroups);
        }

        /**
         * {@code group ('exclusive'|'cooperative') ['multiplicity' '=' value | 'hidden']* [string] '{' ( option | positional | group | use )* '}'}
         * <p>A {@code hidden} group is never actually built as a real {@link ArgGroupSpec}: verified
         * empirically that a group whose every member is hidden still leaves visible artifacts in
         * usage help (an orphaned heading, and, regardless of heading, a stray empty "[]" in the
         * synopsis for the group itself). Instead, {@code hidden} flattens the group's members --
         * each forced hidden, recursively including any nested subgroups' members -- directly into
         * {@code sink}, so nothing group-shaped ever reaches picocli's rendering for it.</p>
         */
        private void parseGroup(Definitions definitions, ArgSink sink) {
            expectKeyword("group");
            String kind = expectWord();
            boolean exclusive;
            if ("exclusive".equals(kind)) {
                exclusive = true;
            } else if ("cooperative".equals(kind)) {
                exclusive = false;
            } else {
                throw new DslParseException("Expected 'exclusive' or 'cooperative' but found '" + kind + "'");
            }

            String multiplicity = null;
            boolean hidden = false;
            String helpSection = null;
            while (checkWord("multiplicity") || checkWord("hidden") || checkWord("helpSection")) {
                String attr = advance().text;
                if ("hidden".equals(attr)) {
                    hidden = true;
                } else if ("helpSection".equals(attr)) {
                    expect(TokenKind.EQUALS, "'='");
                    helpSection = expectWordOrString();
                } else {
                    expect(TokenKind.EQUALS, "'='");
                    multiplicity = expectWordOrString();
                }
            }
            String heading = check(TokenKind.STRING) ? advance().text : null;

            if (hidden) {
                expect(TokenKind.LBRACE, "'{'");
                while (!check(TokenKind.RBRACE)) {
                    if (!check(TokenKind.WORD) || !isMemberKeyword(current().text)) {
                        throw new DslParseException("Expected 'option', 'positional', 'group', or 'use' but found '" + current().text + "'");
                    }
                    parseMember(definitions, new HidingArgSink(sink));
                }
                expect(TokenKind.RBRACE, "'}'");
                return;
            }

            ArgGroupSpec.Builder builder = ArgGroupSpec.builder().exclusive(exclusive);
            if (multiplicity != null) { builder.multiplicity(multiplicity); }
            if (helpSection != null) {
                builder.helpSection(helpSection);
            }
            if (heading != null) { builder.heading(heading); }
            ArgSink groupSink = new GroupArgSink(builder);
            expect(TokenKind.LBRACE, "'{'");
            while (!check(TokenKind.RBRACE)) {
                if (!check(TokenKind.WORD) || !isMemberKeyword(current().text)) {
                    throw new DslParseException("Expected 'option', 'positional', 'group', or 'use' but found '" + current().text + "'");
                }
                parseMember(definitions, groupSink);
            }
            expect(TokenKind.RBRACE, "'}'");
            sink.addGroup(builder.build());
        }

        private OptionSpec parseOption(Definitions definitions) {
            expectKeyword("option");
            List<String> names = new ArrayList<String>();
            names.add(expectWord());
            while (check(TokenKind.COMMA)) {
                advance();
                names.add(expectWord());
            }
            if (!check(TokenKind.COLON)) {
                if (names.size() != 1) {
                    throw new DslParseException("An option reference (no ':') must name exactly one option, got: " + names);
                }
                return definitions.resolveOption(names.get(0));
            }
            advance(); // ':'
            String type = expectWord();

            OptionSpec.Builder builder = OptionSpec.builder(names.toArray(new String[0])).type(ArgTypes.toClass(type));
            if (check(TokenKind.STRING)) {
                builder.description(splitDescription(advance().text));
            }
            while (checkWord("default") || checkWord("required") || checkWord("arity")
                    || checkWord("usageHelp") || checkWord("versionHelp") || checkWord("inherit") || checkWord("hidden")
                    || checkWord("helpSection")) {
                String attr = advance().text;
                if ("required".equals(attr)) {
                    builder.required(true);
                } else if ("usageHelp".equals(attr)) {
                    builder.usageHelp(true);
                } else if ("versionHelp".equals(attr)) {
                    builder.versionHelp(true);
                } else if ("inherit".equals(attr)) {
                    builder.scopeType(picocli.CommandLine.ScopeType.INHERIT);
                } else if ("hidden".equals(attr)) {
                    builder.hidden(true);
                } else if ("helpSection".equals(attr)) {
                    expect(TokenKind.EQUALS, "'='");
                    String section = expectWordOrString();
                    builder.usageHelp(true);
                    builder.helpSection(section);
                } else {
                    expect(TokenKind.EQUALS, "'='");
                    String value = expectWordOrString();
                    if ("default".equals(attr)) { builder.defaultValue(value); } else { builder.arity(value); }
                }
            }
            return builder.build();
        }

        private PositionalParamSpec parsePositional(Definitions definitions) {
            expectKeyword("positional");
            String label = expectWord();
            if (!check(TokenKind.COLON)) {
                return definitions.resolvePositional(label);
            }
            advance(); // ':'
            String type = expectWord();

            PositionalParamSpec.Builder builder = PositionalParamSpec.builder()
                    .paramLabel("<" + label + ">")
                    .type(ArgTypes.toClass(type));
            if (check(TokenKind.STRING)) {
                builder.description(splitDescription(advance().text));
            }
            while (checkWord("default") || checkWord("required") || checkWord("arity") || checkWord("inherit") || checkWord("hidden") || checkWord("helpSection")) {
                String attr = advance().text;
                if ("required".equals(attr)) {
                    builder.required(true);
                } else if ("inherit".equals(attr)) {
                    builder.scopeType(picocli.CommandLine.ScopeType.INHERIT);
                } else if ("hidden".equals(attr)) {
                    builder.hidden(true);
                } else if ("helpSection".equals(attr)) {
                    expect(TokenKind.EQUALS, "'='");
                    builder.helpSection(expectWordOrString());
                } else {
                    expect(TokenKind.EQUALS, "'='");
                    String value = expectWordOrString();
                    if ("default".equals(attr)) { builder.defaultValue(value); } else { builder.arity(value); }
                }
            }
            return builder.build();
        }
    }
}
