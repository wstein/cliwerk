package picocli.spec;

import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.HelpSectionSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.spec.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a picocli {@link CommandSpec} from JSON, and writes a {@link CommandSpec} to JSON.
 * <p>This lets a CLI be defined without an annotated Java class: for example, to give a
 * beautiful, help-and-completion-enabled picocli front end to a third-party tool whose own
 * CLI is not human-friendly, describe that tool's commands/options/positional parameters as
 * JSON (or write it directly in the {@link CommandSpecDsl lightweight DSL}) and build a
 * {@code CommandSpec} from it with {@link #read(String)}.</p>
 * <p>{@link #write(CommandSpec)} does the reverse for <em>any</em> {@code CommandSpec},
 * including ones built from annotated classes, e.g. for documentation or tooling that wants a
 * machine-readable description of a command's options.</p>
 */
public final class CommandSpecJson {

    public static final String SCHEMA_URL = "https://wstein.github.io/cliwerk/spec/schema/command-spec.schema.json";

    private CommandSpecJson() {}

    /** Builds a new {@link CommandSpec} (with any nested subcommands) from the given JSON text. */
    @SuppressWarnings("unchecked")
    public static CommandSpec read(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object describing a command, got: " + json);
        }
        Map<String, Object> root = (Map<String, Object>) parsed;
        Definitions definitions = Definitions.from((Map<String, Object>) root.get("definitions"));
        CommandSpec spec = readCommand(root, definitions);
        SpecValidator.validate(spec);
        return spec;
    }

    /**
     * The document-root {@code "definitions"} dictionary: named option/positional-param
     * templates that a command's own {@code options}/{@code positionalParams} array can
     * reference by name (a plain JSON string) instead of repeating the full definition. Only
     * {@link #read} understands this; {@link #write} always emits fully-inlined objects.
     */
    private static final class Definitions {
        static final Definitions EMPTY = new Definitions(java.util.Collections.<String, Map<String, Object>>emptyMap(),
                java.util.Collections.<String, Map<String, Object>>emptyMap(), java.util.Collections.<String, Bundle>emptyMap());

        final Map<String, Map<String, Object>> options;
        final Map<String, Map<String, Object>> positionalParams;
        final Map<String, Bundle> bundles;

        Definitions(Map<String, Map<String, Object>> options, Map<String, Map<String, Object>> positionalParams,
                    Map<String, Bundle> bundles) {
            this.options = options;
            this.positionalParams = positionalParams;
            this.bundles = bundles;
        }

        @SuppressWarnings("unchecked")
        static Definitions from(Map<String, Object> json) {
            if (json == null) { return EMPTY; }
            Map<String, Map<String, Object>> options = (Map<String, Map<String, Object>>) (Map<String, ?>) mapOrEmpty(json.get("options"));
            Map<String, Map<String, Object>> positionalParams = (Map<String, Map<String, Object>>) (Map<String, ?>) mapOrEmpty(json.get("positionalParams"));

            // soFar wraps this SAME mutable bundles map, so a later bundle in the document can
            // "use" an earlier one -- each bundles.put() below is immediately visible through it.
            Map<String, Bundle> bundles = new LinkedHashMap<String, Bundle>();
            Definitions soFar = new Definitions(options, positionalParams, bundles);
            Map<String, Object> bundlesJson = mapOrEmpty(json.get("bundles"));
            for (Map.Entry<String, Object> entry : bundlesJson.entrySet()) {
                bundles.put(entry.getKey(), Bundle.from((Map<String, Object>) entry.getValue(), soFar));
            }
            return new Definitions(options, positionalParams, bundles);
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> mapOrEmpty(Object value) {
            return value == null ? java.util.Collections.<String, Object>emptyMap() : (Map<String, Object>) value;
        }

        Map<String, Object> resolveOption(String name) {
            Map<String, Object> def = options.get(name);
            if (def == null) {
                throw new IllegalArgumentException("Reference to undefined option \"" + name + "\": not found in \"definitions.options\"");
            }
            return def;
        }

        Map<String, Object> resolvePositional(String label) {
            Map<String, Object> def = positionalParams.get(label);
            if (def == null) {
                throw new IllegalArgumentException("Reference to undefined positional parameter \"" + label + "\": not found in \"definitions.positionalParams\"");
            }
            return def;
        }

        Bundle resolveBundle(String name) {
            Bundle bundle = bundles.get(name);
            if (bundle == null) {
                throw new IllegalArgumentException("Reference to undefined bundle \"" + name + "\": not found in \"definitions.bundles\"");
            }
            return bundle;
        }
    }

    /**
     * A named, reusable bundle of already-defined option/positional names, plus any groups
     * declared inline within the bundle's own {@code "groups"} array, expanded by a
     * {@code "use"} array entry.
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

        @SuppressWarnings("unchecked")
        static Bundle from(Map<String, Object> json, Definitions definitionsSoFar) {
            List<String> optionNames = new ArrayList<String>();
            for (Object name : listOrEmpty(json.get("options"))) {
                definitionsSoFar.resolveOption((String) name); // validates existence eagerly
                optionNames.add((String) name);
            }
            List<String> positionalLabels = new ArrayList<String>();
            for (Object label : listOrEmpty(json.get("positionalParams"))) {
                definitionsSoFar.resolvePositional((String) label);
                positionalLabels.add((String) label);
            }
            List<GroupTemplate> groups = new ArrayList<GroupTemplate>();
            for (Object group : listOrEmpty(json.get("groups"))) {
                groups.add(GroupTemplate.from((Map<String, Object>) group, definitionsSoFar));
            }
            for (Object use : listOrEmpty(json.get("use"))) {
                Bundle used = definitionsSoFar.resolveBundle((String) use);
                optionNames.addAll(used.optionNames);
                positionalLabels.addAll(used.positionalLabels);
                groups.addAll(used.groups);
            }
            return new Bundle(optionNames, positionalLabels, groups);
        }
    }

    /**
     * A group declared inside a bundle's {@code "groups"} array: unlike a normal argGroup
     * object (read and attached immediately by {@link #readArgGroup}), this one may be
     * materialized more than once -- once per {@code "use"} of the bundle -- so its members
     * are stored as templates and cloned fresh (via picocli's own
     * {@code OptionSpec.builder(original)}/{@code PositionalParamSpec.builder(original)}) at
     * each {@link #materialize}. Hidden handling mirrors {@link #readArgGroup}'s exactly.
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

        @SuppressWarnings("unchecked")
        static GroupTemplate from(Map<String, Object> json, Definitions definitions) {
            boolean hidden = Boolean.TRUE.equals(json.get("hidden"));
            Object exclusiveValue = json.get("exclusive");
            boolean exclusive = exclusiveValue == null || (Boolean) exclusiveValue;
            String multiplicity = (String) json.get("multiplicity");
            String heading = (String) json.get("heading");
            String helpSection = (String) json.get("helpSection");

            List<OptionSpec> options = new ArrayList<OptionSpec>();
            for (Object option : listOrEmpty(json.get("options"))) {
                options.add(readOption(resolveOptionJson(option, definitions)));
            }
            List<PositionalParamSpec> positionals = new ArrayList<PositionalParamSpec>();
            for (Object positional : listOrEmpty(json.get("positionalParams"))) {
                positionals.add(readPositional(resolvePositionalJson(positional, definitions)));
            }
            List<GroupTemplate> subgroups = new ArrayList<GroupTemplate>();
            for (Object subgroup : listOrEmpty(json.get("subgroups"))) {
                subgroups.add(GroupTemplate.from((Map<String, Object>) subgroup, definitions));
            }
            for (Object use : listOrEmpty(json.get("use"))) {
                Bundle nested = definitions.resolveBundle((String) use);
                for (String optionName : nested.optionNames) { options.add(readOption(definitions.resolveOption(optionName))); }
                for (String label : nested.positionalLabels) { positionals.add(readPositional(definitions.resolvePositional(label))); }
                subgroups.addAll(nested.groups);
            }
            return new GroupTemplate(exclusive, multiplicity, hidden, helpSection, heading, options, positionals, subgroups);
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
     * body) or an {@link ArgGroupSpec.Builder} (a group body). Mirrors {@link CommandSpecDsl}'s
     * own {@code ArgSink}.
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
     * {@code hidden}. Used for a {@code "hidden": true} argGroup: verified empirically that a
     * group whose every member is hidden still leaves visible artifacts in usage help (an
     * orphaned heading, and, regardless of heading, a stray empty "[]" in the synopsis for the
     * group itself), so a hidden group is never actually built as a real {@link ArgGroupSpec} --
     * its members are flattened directly into the enclosing sink instead. A nested (non-hidden)
     * subgroup reaching {@link #addGroup} is flattened too, recursively.
     */
    private static final class HidingArgSink implements ArgSink {
        private final ArgSink delegate;
        HidingArgSink(ArgSink delegate) { this.delegate = delegate; }
        public void addOption(OptionSpec option) { delegate.addOption(OptionSpec.builder(option).hidden(true).build()); }
        public void addPositional(PositionalParamSpec positional) { delegate.addPositional(PositionalParamSpec.builder(positional).hidden(true).build()); }
        public void addGroup(ArgGroupSpec group) {
            for (ArgSpec arg : group.args()) {
                if (arg.isOption()) { addOption((OptionSpec) arg); } else { addPositional((PositionalParamSpec) arg); }
            }
            for (ArgGroupSpec subgroup : group.subgroups()) { addGroup(subgroup); }
        }
    }

    /** Serializes the given {@link CommandSpec} (with any nested subcommands) to JSON text. */
    public static String write(CommandSpec spec) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("$schema", SCHEMA_URL);
        root.putAll(writeCommand(spec));
        return Json.write(root);
    }

    // ---- reading: JSON -> CommandSpec ----

    @SuppressWarnings("unchecked")
    private static CommandSpec readCommand(Map<String, Object> json, Definitions definitions) {
        CommandSpec spec = CommandSpec.create();
        String name = (String) json.get("name");
        if (name != null) {
            spec.name(name);
        }
        String[] description = readStringArray(json.get("description"));
        if (description != null) {
            spec.usageMessage().description(description);
        }
        String helpSection = (String) json.get("helpSection");
        if (helpSection != null) {
            spec.helpSection(helpSection);
        }
        List<Object> helpSections = (List<Object>) json.get("helpSections");
        if (helpSections != null) {
            for (Object item : helpSections) {
                if (item instanceof Map) {
                    Map<String, Object> secMap = (Map<String, Object>) item;
                    String secName = (String) secMap.get("name");
                    if (secName != null && !secName.isEmpty()) {
                        HelpSectionSpec.Builder secBuilder = HelpSectionSpec.builder(secName);
                        String heading = (String) secMap.get("heading");
                        if (heading != null) { secBuilder.heading(heading); }
                        String[] desc = readStringArray(secMap.get("description"));
                        if (desc != null) { secBuilder.description(desc); }
                        String emptyMessage = (String) secMap.get("emptyMessage");
                        if (emptyMessage != null) { secBuilder.emptyMessage(emptyMessage); }
                        String notice = (String) secMap.get("notice");
                        if (notice != null) { secBuilder.notice(notice); }
                        spec.addHelpSectionSpec(secBuilder.build());
                    }
                }
            }
        }

        ArgSink sink = new CommandArgSink(spec);
        addOptionsPositionalsAndUses(json, definitions, sink);
        for (Object argGroup : listOrEmpty(json.get("argGroups"))) {
            readArgGroup((Map<String, Object>) argGroup, definitions, sink);
        }
        for (Object subcommand : listOrEmpty(json.get("subcommands"))) {
            Map<String, Object> subJson = (Map<String, Object>) subcommand;
            if (subJson.containsKey("definitions")) {
                throw new IllegalArgumentException("Subcommand \"" + subJson.get("name")
                        + "\" has a nested \"definitions\" block, but definitions are only read at the"
                        + " document root; move those entries to the document root's \"definitions\","
                        + " from where every command can reference them.");
            }
            CommandSpec subSpec = readCommand(subJson, definitions);
            spec.addSubcommand(subSpec.name(), subSpec);
        }
        Object mixinHelp = json.get("mixinStandardHelpOptions");
        if (mixinHelp != null && (Boolean) mixinHelp) {
            spec.mixinStandardHelpOptions(true);
        }
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveOptionJson(Object entry, Definitions definitions) {
        return entry instanceof String ? definitions.resolveOption((String) entry) : (Map<String, Object>) entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolvePositionalJson(Object entry, Definitions definitions) {
        return entry instanceof String ? definitions.resolvePositional((String) entry) : (Map<String, Object>) entry;
    }

    /** Reads a {@code "options"}/{@code "positionalParams"}/{@code "use"} triple into {@code sink} -- shared by a command and an argGroup object, which both accept the same three fields. */
    private static void addOptionsPositionalsAndUses(Map<String, Object> json, Definitions definitions, ArgSink sink) {
        for (Object option : listOrEmpty(json.get("options"))) {
            sink.addOption(readOption(resolveOptionJson(option, definitions)));
        }
        for (Object positional : listOrEmpty(json.get("positionalParams"))) {
            sink.addPositional(readPositional(resolvePositionalJson(positional, definitions)));
        }
        for (Object use : listOrEmpty(json.get("use"))) {
            Bundle bundle = definitions.resolveBundle((String) use);
            for (String optionName : bundle.optionNames) {
                sink.addOption(readOption(definitions.resolveOption(optionName)));
            }
            for (String label : bundle.positionalLabels) {
                sink.addPositional(readPositional(definitions.resolvePositional(label)));
            }
            for (GroupTemplate group : bundle.groups) {
                group.materialize(sink);
            }
        }
    }

    /**
     * Reads one argGroup object into {@code sink}: either {@code sink.addGroup(...)} with a
     * freshly-built {@link ArgGroupSpec}, or -- if {@code "hidden": true} -- flattens its members
     * (and any subgroups', recursively) directly into {@code sink} instead. See {@link HidingArgSink}.
     */
    @SuppressWarnings("unchecked")
    private static void readArgGroup(Map<String, Object> json, Definitions definitions, ArgSink sink) {
        if (Boolean.TRUE.equals(json.get("hidden"))) {
            ArgSink hidingSink = new HidingArgSink(sink);
            addOptionsPositionalsAndUses(json, definitions, hidingSink);
            for (Object subgroup : listOrEmpty(json.get("subgroups"))) {
                readArgGroup((Map<String, Object>) subgroup, definitions, hidingSink);
            }
            return;
        }
        ArgGroupSpec.Builder builder = ArgGroupSpec.builder();
        Object exclusive = json.get("exclusive");
        if (exclusive != null) { builder.exclusive((Boolean) exclusive); }
        String multiplicity = (String) json.get("multiplicity");
        if (multiplicity != null) { builder.multiplicity(multiplicity); }
        String heading = (String) json.get("heading");
        if (heading != null) { builder.heading(heading); }
        String helpSection = (String) json.get("helpSection");
        if (helpSection != null) {
            builder.helpSection(helpSection);
        }
        ArgSink groupSink = new GroupArgSink(builder);
        addOptionsPositionalsAndUses(json, definitions, groupSink);
        for (Object subgroup : listOrEmpty(json.get("subgroups"))) {
            readArgGroup((Map<String, Object>) subgroup, definitions, groupSink);
        }
        sink.addGroup(builder.build());
    }

    @SuppressWarnings("unchecked")
    private static OptionSpec readOption(Map<String, Object> json) {
        List<Object> namesJson = (List<Object>) json.get("names");
        if (namesJson == null || namesJson.isEmpty()) {
            throw new IllegalArgumentException("Option is missing required \"names\" array: " + json);
        }
        String[] names = new String[namesJson.size()];
        for (int i = 0; i < names.length; i++) { names[i] = (String) namesJson.get(i); }

        OptionSpec.Builder builder = OptionSpec.builder(names);
        String type = (String) json.get("type");
        if (type != null) { builder.type(ArgTypes.toClass(type)); }
        String[] description = readStringArray(json.get("description"));
        if (description != null) { builder.description(description); }
        Object defaultValue = json.get("defaultValue");
        if (defaultValue != null) { builder.defaultValue(String.valueOf(defaultValue)); }
        Object required = json.get("required");
        if (required != null) { builder.required((Boolean) required); }
        String arity = (String) json.get("arity");
        if (arity != null) { builder.arity(arity); }
        Object usageHelp = json.get("usageHelp");
        if (usageHelp != null) { builder.usageHelp((Boolean) usageHelp); }
        Object versionHelp = json.get("versionHelp");
        if (versionHelp != null) { builder.versionHelp((Boolean) versionHelp); }
        String helpSection = (String) json.get("helpSection");
        if (helpSection != null) {
            builder.usageHelp(true);
            builder.helpSection(helpSection);
        }
        Object hidden = json.get("hidden");
        if (hidden != null) { builder.hidden((Boolean) hidden); }
        if (isInheritScope(json)) { builder.scopeType(picocli.CommandLine.ScopeType.INHERIT); }
        return builder.build();
    }

    private static PositionalParamSpec readPositional(Map<String, Object> json) {
        PositionalParamSpec.Builder builder = PositionalParamSpec.builder();
        String paramLabel = (String) json.get("paramLabel");
        if (paramLabel != null) { builder.paramLabel(paramLabel); }
        String type = (String) json.get("type");
        if (type != null) { builder.type(ArgTypes.toClass(type)); }
        String[] description = readStringArray(json.get("description"));
        if (description != null) { builder.description(description); }
        Object defaultValue = json.get("defaultValue");
        if (defaultValue != null) { builder.defaultValue(String.valueOf(defaultValue)); }
        Object required = json.get("required");
        if (required != null) { builder.required((Boolean) required); }
        String arity = (String) json.get("arity");
        if (arity != null) { builder.arity(arity); }
        String helpSection = (String) json.get("helpSection");
        if (helpSection != null) { builder.helpSection(helpSection); }
        Object hidden = json.get("hidden");
        if (hidden != null) { builder.hidden((Boolean) hidden); }
        if (isInheritScope(json)) { builder.scopeType(picocli.CommandLine.ScopeType.INHERIT); }
        return builder.build();
    }

    private static boolean isInheritScope(Map<String, Object> json) {
        return "inherit".equals(json.get("scope"));
    }

    // ---- writing: CommandSpec -> JSON ----

    private static Map<String, Object> writeCommand(CommandSpec spec) {
        Map<String, Object> json = new LinkedHashMap<String, Object>();
        json.put("name", spec.name());
        putDescriptionIfPresent(json, spec.usageMessage().description());
        String helpSection = Help.getHelpSection(spec);
        if (helpSection != null) {
            json.put("helpSection", helpSection);
        }
        if (!spec.helpSectionSpecs().isEmpty()) {
            List<Object> sections = new ArrayList<Object>();
            for (HelpSectionSpec sec : spec.helpSectionSpecs().values()) {
                Map<String, Object> secJson = new LinkedHashMap<String, Object>();
                secJson.put("name", sec.name());
                if (sec.heading() != null && !sec.heading().isEmpty()) {
                    secJson.put("heading", sec.heading());
                }
                putDescriptionIfPresent(secJson, sec.description());
                if (sec.emptyMessage() != null && !sec.emptyMessage().isEmpty()) {
                    secJson.put("emptyMessage", sec.emptyMessage());
                }
                if (sec.notice() != null && !sec.notice().isEmpty()) {
                    secJson.put("notice", sec.notice());
                }
                sections.add(secJson);
            }
            json.put("helpSections", sections);
        }
        if (spec.mixinStandardHelpOptions()) {
            json.put("mixinStandardHelpOptions", Boolean.TRUE);
        }

        Set<OptionSpec> standardHelpOptions = Collections.emptySet();
        if (spec.mixinStandardHelpOptions()) {
            CommandSpec helpMixin = spec.mixins().get("mixinStandardHelpOptions");
            if (helpMixin != null) {
                standardHelpOptions = new HashSet<OptionSpec>(helpMixin.options());
            }
        }

        List<Object> options = new ArrayList<Object>();
        for (OptionSpec option : spec.options()) {
            if (option.group() == null && !option.inherited() && !standardHelpOptions.contains(option)) {
                options.add(writeOption(option));
            }
        }
        if (!options.isEmpty()) { json.put("options", options); }

        List<Object> positionals = new ArrayList<Object>();
        for (PositionalParamSpec positional : spec.positionalParameters()) {
            if (positional.group() == null && !positional.inherited()) { positionals.add(writePositional(positional)); }
        }
        if (!positionals.isEmpty()) { json.put("positionalParams", positionals); }

        if (!spec.argGroups().isEmpty()) {
            List<Object> argGroups = new ArrayList<Object>();
            for (ArgGroupSpec group : spec.argGroups()) {
                argGroups.add(writeArgGroup(group));
            }
            json.put("argGroups", argGroups);
        }
        if (!spec.subcommands().isEmpty()) {
            List<Object> subcommands = new ArrayList<Object>();
            for (CommandLine subcommand : spec.subcommands().values()) {
                subcommands.add(writeCommand(subcommand.getCommandSpec()));
            }
            json.put("subcommands", subcommands);
        }
        return json;
    }

    private static Map<String, Object> writeOption(OptionSpec option) {
        Map<String, Object> json = new LinkedHashMap<String, Object>();
        json.put("names", new ArrayList<Object>(java.util.Arrays.asList(option.names())));
        putCommonArgSpecFields(json, option);
        String helpSection = Help.getHelpSection(option);
        if (helpSection != null) {
            json.put("helpSection", helpSection);
        } else if (option.usageHelp()) {
            json.put("usageHelp", Boolean.TRUE);
        }
        if (option.versionHelp()) { json.put("versionHelp", Boolean.TRUE); }
        return json;
    }

    private static Map<String, Object> writePositional(PositionalParamSpec positional) {
        Map<String, Object> json = new LinkedHashMap<String, Object>();
        json.put("paramLabel", positional.paramLabel());
        putCommonArgSpecFields(json, positional);
        String helpSection = Help.getHelpSection(positional);
        if (helpSection != null && !helpSection.isEmpty()) {
            json.put("helpSection", helpSection);
        }
        return json;
    }

    private static Map<String, Object> writeArgGroup(ArgGroupSpec group) {
        Map<String, Object> json = new LinkedHashMap<String, Object>();
        json.put("exclusive", group.exclusive());
        json.put("multiplicity", group.multiplicity().toString());
        String helpSection = Help.getHelpSection(group);
        if (helpSection != null) { json.put("helpSection", helpSection); }
        if (group.heading() != null) { json.put("heading", group.heading()); }

        List<Object> options = new ArrayList<Object>();
        List<Object> positionals = new ArrayList<Object>();
        for (ArgSpec arg : group.args()) {
            if (arg.isOption()) { options.add(writeOption((OptionSpec) arg)); } else { positionals.add(writePositional((PositionalParamSpec) arg)); }
        }
        if (!options.isEmpty()) { json.put("options", options); }
        if (!positionals.isEmpty()) { json.put("positionalParams", positionals); }

        if (!group.subgroups().isEmpty()) {
            List<Object> subgroups = new ArrayList<Object>();
            for (ArgGroupSpec subgroup : group.subgroups()) { subgroups.add(writeArgGroup(subgroup)); }
            json.put("subgroups", subgroups);
        }
        return json;
    }

    private static void putCommonArgSpecFields(Map<String, Object> json, picocli.CommandLine.Model.ArgSpec arg) {
        json.put("type", ArgTypes.toName(arg.type()));
        putDescriptionIfPresent(json, arg.description());
        if (arg.defaultValue() != null) {
            json.put("defaultValue", arg.defaultValue());
        }
        if (arg.required()) {
            json.put("required", Boolean.TRUE);
        }
        json.put("arity", arg.arity().toString());
        if (arg.hidden()) {
            json.put("hidden", Boolean.TRUE);
        }
        if (arg.scopeType() == picocli.CommandLine.ScopeType.INHERIT) {
            json.put("scope", "inherit");
        }
    }

    private static void putDescriptionIfPresent(Map<String, Object> json, String[] description) {
        if (description != null && description.length > 0) {
            json.put("description", new ArrayList<Object>(java.util.Arrays.asList((Object[]) description)));
        }
    }

    // ---- helpers ----

    private static String[] readStringArray(Object value) {
        if (value == null) { return null; }
        List<?> list = (List<?>) value;
        String[] result = new String[list.size()];
        for (int i = 0; i < result.length; i++) { result[i] = (String) list.get(i); }
        return result;
    }

    private static List<?> listOrEmpty(Object value) {
        return value == null ? java.util.Collections.emptyList() : (List<?>) value;
    }
}
