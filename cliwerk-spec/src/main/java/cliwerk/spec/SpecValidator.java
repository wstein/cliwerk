package cliwerk.spec;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Checks a built {@link CommandSpec} tree for a picocli parsing ambiguity that's easy to
 * introduce by hand: an option's {@code defaultValue} that's textually identical to a sibling
 * subcommand's name. This is not a picocli bug -- the parser is being conservative about
 * genuinely ambiguous input -- but it fails unconditionally, on <em>every</em> invocation of the
 * command (confirmed empirically: even with zero arguments given, applying the default alone
 * throws {@code MissingParameterException}), so it's far better caught here than at first use.
 * <p>An earlier version of this check also flagged an unbounded-arity positional sibling to
 * subcommands, on the theory that it could "swallow" a subcommand name. That theory turned out
 * to be wrong: empirically, picocli correctly dispatches into a subcommand whose name appears as
 * a token even with such a positional active (with or without preceding positional values already
 * consumed) -- the real failure mode in the original flix example was simply positional
 * parameters declared on the wrong command level relative to where their values actually appear
 * in a real invocation, which is an ordinary structural mistake, not a subtle parsing ambiguity,
 * and isn't something this validator attempts to detect.</p>
 */
final class SpecValidator {

    private SpecValidator() {}

    /** @throws IllegalArgumentException if the tree rooted at {@code spec} contains the ambiguity, at any depth. */
    static void validate(CommandSpec spec) {
        for (OptionSpec option : spec.options()) {
            String defaultValue = option.defaultValue();
            if (defaultValue != null && spec.subcommands().containsKey(defaultValue)) {
                throw new IllegalArgumentException("Command \"" + spec.name() + "\"'s option \"" + option.longestName()
                        + "\" has defaultValue \"" + defaultValue + "\", which is also the name of a sibling "
                        + "subcommand: picocli rejects this default value when applying it, since it looks like a "
                        + "subcommand token -- this fails on every invocation, even with no arguments at all. Pick "
                        + "a defaultValue that doesn't double as a subcommand name.");
            }
        }
        for (picocli.CommandLine subcommand : spec.subcommands().values()) {
            validate(subcommand.getCommandSpec());
        }
    }
}
