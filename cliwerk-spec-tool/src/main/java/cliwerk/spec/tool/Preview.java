package cliwerk.spec.tool;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cliwerk.spec.tool.PreviewCommand.Format;

/**
 * Prints the usage help message for a {@link CommandSpec} and, recursively, every one of its
 * subcommands -- so an author can see exactly what CLI a cliwerk-spec file produces without
 * writing any Java or wiring up execution. Optionally also renders one or more of the command's
 * tagged on-demand {@linkplain CommandSpec#helpSections() help sections} (e.g. "experimental"),
 * for every command in the tree that declares that section, and/or wraps each command's output
 * in a fenced Markdown code block prefixed with the shell command that would produce it.
 */
final class Preview {

    private Preview() {
    }

    static void render(CommandSpec spec, PrintWriter out, Ansi ansi) {
        render(spec, out, ansi, Collections.<String>emptyList(), null, Format.text);
    }

    static void render(CommandSpec spec, PrintWriter out, Ansi ansi, List<String> sections, String triggerOption) {
        render(spec, out, ansi, sections, triggerOption, Format.text);
    }

    /**
     * @param sections      names of on-demand help sections to additionally render for every command
     *                      that declares them (e.g. {@code "experimental"}); empty to render only
     *                      the standard usage help
     * @param triggerOption an explicit option to pass when rendering a section, overriding automatic
     *                      discovery of the {@code usageHelp} option tagged with that section; {@code null}
     *                      to auto-discover the trigger for each command
     * @param format        {@link Format#text} to print plain usage help, or {@link Format#markdown} to wrap
     *                      each command's output in a fenced code block prefixed with a {@code $ <qualified
     *                      command name> --help} prompt line
     */
    static void render(CommandSpec spec, PrintWriter out, Ansi ansi, List<String> sections, String triggerOption, Format format) {
        CommandLine root = new CommandLine(spec);
        render(root, root, Collections.<String>emptyList(), out, ansi, sections, triggerOption, format, new LinkedHashSet<CommandLine>());
    }

    private static void render(CommandLine root, CommandLine cmd, List<String> path, PrintWriter out, Ansi ansi,
                                List<String> sections, String triggerOption, Format format, Set<CommandLine> done) {
        if (!done.add(cmd)) {
            return; // an alias resolves to the same CommandLine instance as its primary name; don't print it twice
        }

        if (format == Format.markdown) {
            StringWriter buffer = new StringWriter();
            PrintWriter bufferedOut = new PrintWriter(buffer);
            cmd.usage(bufferedOut, ansi);
            renderRequestedSections(root, cmd, path, bufferedOut, ansi, sections, triggerOption);
            bufferedOut.flush();
            writeMarkdownBlock(qualifiedName(root, path), buffer.toString(), out);
        } else {
            cmd.usage(out, ansi);
            renderRequestedSections(root, cmd, path, out, ansi, sections, triggerOption);
        }

        for (Map.Entry<String, CommandLine> entry : cmd.getSubcommands().entrySet()) {
            out.println();
            List<String> childPath = new ArrayList<String>(path);
            childPath.add(entry.getKey());
            render(root, entry.getValue(), childPath, out, ansi, sections, triggerOption, format, done);
        }
    }

    private static String qualifiedName(CommandLine root, List<String> path) {
        StringBuilder sb = new StringBuilder(root.getCommandSpec().name());
        for (String name : path) {
            sb.append(' ').append(name);
        }
        return sb.toString();
    }

    private static void writeMarkdownBlock(String qualifiedName, String content, PrintWriter out) {
        out.println("```");
        out.println("$ " + qualifiedName + " --help");
        out.print(content);
        if (content.length() > 0 && content.charAt(content.length() - 1) != '\n') {
            out.println();
        }
        out.println("```");
    }

    private static void renderRequestedSections(CommandLine root, CommandLine cmd, List<String> path, PrintWriter out, Ansi ansi,
                                                  List<String> sections, String triggerOption) {
        CommandSpec spec = cmd.getCommandSpec();
        for (String section : sections) {
            if (!spec.helpSections().contains(section)) {
                continue; // this command has no content tagged with this section
            }
            String trigger = triggerOption != null ? triggerOption : findTrigger(spec, section);
            out.println();
            if (trigger != null) {
                renderViaTrigger(root, cmd, path, out, ansi, trigger);
            } else {
                cmd.printHelpSection(section, out); // no usageHelp trigger declared for this section; render its raw content directly
            }
        }
    }

    private static String findTrigger(CommandSpec spec, String section) {
        Optional<OptionSpec> trigger = spec.findHelpSectionTrigger(section);
        return trigger.isPresent() ? trigger.get().longestName() : null;
    }

    /**
     * Parses the trigger option from the root command down the given subcommand path -- so that
     * picocli's parent-chain bookkeeping (which requires every ancestor to have actually been
     * parsed) stays consistent -- then renders the leaf command's usage directly.
     * <p>Deliberately uses {@code parseArgs} + {@code usage} rather than {@code execute}: the
     * default {@code RunLast} execution strategy prints on-demand help through a legacy,
     * {@code PrintStream}-based backwards-compatibility path (see
     * {@code CommandLine#printHelpIfRequested(List, PrintStream, PrintStream, Help.ColorScheme)})
     * that unconditionally overwrites every matched command's configured writer, silently
     * discarding any {@code setOut} redirection made beforehand.
     */
    private static void renderViaTrigger(CommandLine root, CommandLine leaf, List<String> path, PrintWriter out, Ansi ansi, String trigger) {
        List<String> args = new ArrayList<String>(path);
        args.add(trigger);
        String[] argsArray = args.toArray(new String[0]);
        try {
            root.parseArgs(argsArray);
            leaf.usage(out, ansi);
        } catch (CommandLine.ParameterException ex) {
            root.getErr().println(ex.getMessage());
        }
    }
}
