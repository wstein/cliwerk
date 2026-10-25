# Changelog

All notable changes to cliwerk (an independent fork of picocli) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Since 4.7.7.1, versions are `<upstream x.y.z>.<fork revision>`: the first three parts are the upstream picocli
release whose API is the baseline, the fourth part counts cliwerk revisions on top of it (see [docs/FORK.adoc](docs/FORK.adoc)).
The releases 4.7.8 to 4.9.4 below were cliwerk's own releases, not upstream picocli releases; they have been withdrawn and deleted.

---

## [v4.7.7.1] - 2026-10-25

### Changed
- **Versioning**: first version under the four-part scheme `<upstream x.y.z>.<fork revision>`; API baseline is upstream picocli 4.7.7.
- **Coordinates**: published as `io.github.wstein:cliwerk` (was `io.github.wstein:picocli`) in the Maven repository
  `https://wstein.github.io/cliwerk/maven/`; the GitHub Pages URLs (Javadoc, JSON Schema `$id`) moved from
  `wstein.github.io/picocli` to `wstein.github.io/cliwerk`. Only the latest JSON Schema is published (no versioned copies).
  The Java package stays `picocli`. Release jars on GitHub keep the names `picocli-<version>*.jar`.
- **`CommandLine.VERSION`** now reports the cliwerk version (`4.7.7.1`); the withdrawn/earlier releases reported `4.7.8-SNAPSHOT`
  (for example in `--version` output of `AutoComplete` and in generated completion script headers).
- Contains the cliwerk changes of the withdrawn/earlier releases below plus changes on `develop` that are not listed under them, notably:
  - **Fish completion** reworked (the version reviewed for upstream, remkop/picocli#2463): completion of negated options,
    options with an optional value no longer force a value, escaping of candidates, one condition per `complete` line;
    the generated scripts are verified in real fish shells in CI.
  - **Shell-independent completion model** (package-private), from which the fish and PowerShell scripts are rendered.
  - **PowerShell completion** (Windows PowerShell 5.1 and PowerShell 7+ on Windows, Linux and macOS):
    `AutoComplete.powershell()` generates a script that registers a native argument completer
    (`Register-ArgumentCompleter -Native`) for subcommands (with aliases), options (including negated names of negatable options),
    enum constants and other completion candidates, with the descriptions as tooltips.
    `generate-completion --shell=powershell` and `picocli.AutoComplete --shell=powershell` (which writes `<commandName>-completion.ps1`)
    produce it, and so does `picospec completion --shell=powershell`.
    The golden scripts are loaded in real PowerShell sessions in CI (`powershell-completion.yml`).
    Note that `GenerateCompletion.Shell` has a new constant `powershell`, and the `--shell` candidates of `generate-completion` and `picocli.AutoComplete` now include it.
  - **`picospec preview`**: `--section` and `--trigger-option` to render on-demand help sections, and `--format text|markdown`.

---

## [v4.9.4] - 2026-09-21

### Added
- **Command-Level `helpSection`**:
  - `@Command(helpSection = "...")` and `CommandSpec.helpSection(String)` allow tagging commands and subcommands with an on-demand help section name (e.g. `"experimental"`).
  - Tagged subcommands are automatically omitted from the parent command's standard `--help` command list.
  - When the corresponding section is triggered (e.g. `--Xhelp`), tagged subcommands are rendered in their own command list alongside tagged options and groups.
- **Positional Parameter `helpSection`**:
  - `@Parameters(helpSection = "...")`, `PositionalParamSpec.Builder.helpSection(String)`, and `ArgSpec.helpSection()` allow tagging positional parameters with on-demand help section names.
  - Tagged positional parameters are automatically omitted from standard `--help` usage and detailed synopsis, and rendered only when the matching help section is requested.
  - `CommandSpec.findHelpSectionForPositional(String paramLabel)`: returns `Optional<String>` for the section name associated with a positional parameter.
  - DSL syntax: `positional <label> : <type> helpSection="<section>" ...`.
  - JSON Schema: Added `"helpSection"` property to `positionalParam` in `command-spec.schema.json`.
- **Help Section Metadata & Customization (`HelpSectionSpec` / `@HelpSection`)**:
  - `HelpSectionSpec` model and `@HelpSection` / `@HelpSections` annotations for declaring section metadata: `heading`, `description`, `emptyMessage`, and `notice`.
  - Supported via `@Command(helpSections = { ... })` and `CommandSpec.addHelpSectionSpec(HelpSectionSpec)`.
  - When rendered on demand, loose options and commands receive the declared section heading and description.
  - Configurable empty fallback message (`emptyMessage`) returned when no elements match the section on that command (instead of an empty string).
  - DSL syntax: `section <name> ["<description>"] [heading="..."] [emptyMessage="..."] [notice="..."]`.
  - JSON Schema: Added `"helpSections"` array property on `command` objects in `command-spec.schema.json`.
- **Automated Help Sections Notice in Standard Usage Help**:
  - `UsageMessageSpec.SECTION_KEY_HELP_SECTIONS_NOTICE` ("helpSectionsNotice") added to default usage section layout.
  - `Help.helpSectionsNotice()` auto-generates a footer note (e.g. `Run '<cmd> --Xhelp' to view experimental options and commands.`) when a command has elements in that section and a trigger option is present.
  - Custom notice support via `HelpSectionSpec.notice()` or `@HelpSection(notice = "...")`.
  - Configurable via `UsageMessageSpec.showHelpSectionsNotice(boolean)` or `@Command(showHelpSectionsNotice = true|false)`.
- **Help Section Discovery & Trigger Lookup API**:
  - `CommandSpec.helpSections()`: returns an unmodifiable `Set<String>` of all help section names in the command tree.
  - `CommandSpec.findHelpSectionTrigger(String sectionName)`: returns `Optional<OptionSpec>` for the option marked `usageHelp=true` triggering that section.
  - `CommandSpec.findHelpSectionForOption(String optionName)`: returns `Optional<String>` for the section name associated with an option or its parent group.
  - `CommandLine.printHelpSection(String sectionName, PrintWriter out)` and `CommandLine.printHelpSection(String sectionName, PrintStream out)`: direct programmatic rendering of tagged help sections.
- **DSL and JSON Schema Support for Command `helpSection`**:
  - DSL syntax: `command <name> helpSection="<section>" ... { ... }`.
  - JSON Schema: Added `"helpSection"` property to `command` definition in `command-spec.schema.json`.
  - Bidirectional serialization and round-trip support in `CommandSpecDsl` and `CommandSpecJson`.

---

## [v4.9.3] - 2026-09-21

### Added
- **Bundled `picocli-spec` in main jar**:
  - The published `picocli-${version}.jar` now shades `picocli.spec.*` (`CommandSpecDsl`, `CommandSpecJson`, `CommandSpecMerger`) when built on Java 11+, allowing single-jar consumers (like `flixw`) to parse DSL/JSON specs without a separate dependency.

### Changed
- **Spec Migration to flixw**:
  - Retained `flix-0.60.0.picocli` as the canonical real-world DSL example in `picocli-spec/examples/`, migrating the remaining version range progression (`v0.67.0` to `v0.76.2`) to `flixw`.

---

## [v4.9.2] - 2026-09-21

### Added
- **`mixinStandardHelpOptions` in picocli-spec**:
  - DSL support: `mixinStandardHelpOptions` directive inside command blocks.
  - JSON Schema support: `"mixinStandardHelpOptions": true` property on command objects in `command-spec.schema.json`.
  - Core integration: Automatically calls `CommandSpec#mixinStandardHelpOptions(true)` without manual `-h`/`--help` and `-V`/`--version` definitions.
  - Lossless Serialization: `CommandSpecJson.write()` and `CommandSpecDsl.write()` emit `mixinStandardHelpOptions` while suppressing duplicated synthetic standard help options in flat option lists.
- **Complete Flix CLI Spec Suite (v0.60.0 – v0.76.2)**:
  - Scanned and mapped all distinct CLI evolution points across 31 git tags in flix's history, producing targeted `.picocli` specifications and test suites:
    - `flix-0.67.0.picocli`: Added `clean` subcommand.
    - `flix-0.67.1.picocli`: Added `format` subcommand, file-argument restrictions, removal of `--args`, reduction to 10 experimental flags.
    - `flix-0.68.0.picocli`: Added `eff-check` and `eff-lock` subcommands, removed compiler `--explain` flag.
    - `flix-0.73.0.picocli`: Added `--top` compiler profiling flag.
    - `flix-0.75.2.picocli`: Added experimental `--Xnewmono` flag (11 experimental flags).
    - `flix-0.75.3.picocli`: Added `build-classes` subcommand, updated descriptions for `clean` and `--Xprint-phases`.
    - `flix-0.76.0.picocli`: Added `stat` subcommand, added `--Xverify`, removed `--Xsummary`.
  - Added CLI version range progression matrix in `picocli-spec/examples/README.md`.
- **Repository AI Agent Guidance**: Added canonical `AGENTS.md` and `.github/copilot-instructions.md` for AI workflows.

### Changed
- **Flix Examples Modernization**: Updated all flix specs to use `mixinStandardHelpOptions` and `Path[]` for positional file arguments.

---

## [v4.9.1] - 2026-09-21

### Added
- **Java 11 Target**: `picocli-spec` and `picocli-spec-tool` now target Java 11 bytecode (`sourceCompatibility = 11`, `targetCompatibility = 11`).
- **Extended `ArgTypes` Vocabulary**: Added `Path`, `URI`, `URL`, `BigDecimal`, and `BigInteger` with built-in converter mappings and JSON schema synchronization.
- **Batch Spec Merging**: Added `CommandSpecMerger.mergeAll(CommandSpec uber, Collection<CommandSpec> imported)` alongside varargs `merge()`.
- **Manpage Overwrite Guard**: `picospec manpage` checks for existing `.adoc` files before writing and requires `--overwrite` to prevent accidental loss.
- **Spec-Driven Development (SDD) Docs**:
  - Split `picocli-spec/README.md` into `REQUIREMENTS.md` (FR-1–FR-7, NFR-1–NFR-4), `DESIGN.md` (EBNF grammar, data flow, schema reference), and a concise `README.md`.
  - Added Architecture Decision Records under `docs/adr/`: `ADR-001` (Custom Minimal JSON Parser), `ADR-002` (Dual DSL & JSON Strategy), and `ADR-003` (Two-Module Split).
- **Fork-First Root README**: Overhauled repository `README.md` to highlight fork features, coordinates, and usage.

### Changed
- **JDK Support Policy**: CI matrix streamlined to LTS JDK releases (8, 11, 17, 21); dropped non-blocking intermediate JDK jobs and legacy `build-java-6-7` CI job.
- **CI Dependabot Configuration**: Auto-merge restricted to `version-update:semver-patch`.

### Fixed
- **DSL Multi-line Descriptions**: `CommandSpecDsl.parse()` splits descriptions on newline escapes (`\n`) and carriage returns into `String[]`, ensuring round-trip fidelity with `write()`.
- **Subcommand Definitions Guard**: `CommandSpecJson.read()` rejects nested `definitions` blocks on subcommand JSON objects with a helpful error.
- **Fish Completion Robustness**: Verified quoting of single quotes, backslashes, and script/subcommand names in fish completions.

---

## [v4.9.0] - 2026-09-20

### Added

#### `picocli-spec` (New Module)
- **DSL Specification Parser & Serializer**: Define complete picocli `CommandSpec` trees using a concise, human-friendly DSL (`.picocli`) via `CommandSpecDsl.parse()` and `CommandSpecDsl.write()`.
- **JSON Specification Reader & Writer**: Parse and serialize `CommandSpec` trees from/to JSON adhering to `command-spec.schema.json` via `CommandSpecJson.read()` and `CommandSpecJson.write()`.
- **Zero Runtime Dependencies**: Embedded lightweight JSON parser (`picocli.spec.json.Json`) ensuring zero transitive dependencies beyond picocli core.
- **Spec Composition**: `CommandSpecMerger.merge(CommandSpec uber, CommandSpec... imported)` attaches external specs as subcommands onto a host CLI.
- **Definition Reuse & Bundles**:
  - Top-level `definitions` blocks in DSL and JSON for defining reusable options and positional parameters.
  - `bundle` declarations grouping options, positionals, and argument groups, expandable into commands via `use <bundleName>`.
  - Nested bundle composition (`bundle` using an earlier `bundle`).
- **Base Type Vocabulary (`ArgTypes`)**:
  - Built-in mappings for `String`, `boolean`, `int`, `long`, `double`, and `File`.
  - Array suffix support (e.g. `File[]`, `String[]`) for multi-value arguments.
- **Ambiguity Validation (`SpecValidator`)**: Fails fast during parse or merge on parsing ambiguities, such as an option's `defaultValue` colliding with a sibling subcommand's name.

#### `picocli-spec-tool` (New Module)
- **Standalone `picospec` CLI**: A self-contained utility for working with spec files without writing Java code.
- **Subcommands**:
  - `preview <spec-file>`: Renders recursive usage help for a spec and all its subcommands.
  - `validate <spec-file>`: Fast load-only pass/fail validation for pre-commit hooks and CI.
  - `completion <spec-file>`: Generates bash, zsh, or fish shell completion scripts.
  - `manpage <spec-file>`: Generates AsciiDoc man pages via `picocli-codegen`.
  - `convert <spec-file>`: Losslessly converts between `.picocli` DSL and JSON formats.
- **Packaging**: Self-contained executable fat-jar (`picocli-spec-tool-<version>-all.jar`) built via Gradle Shadow plugin.

#### Core Enhancements & Features
- **On-Demand Help Sections (`helpSection`)**:
  - Added `helpSection` attribute to `@Option` and `@ArgGroup` annotations and model classes.
  - Tagged options (e.g. `--Xhelp`) act as on-demand triggers that render only their tagged section and exit.
  - Tagged options and groups are excluded from default `--help` output while remaining discoverable in shell autocompletions.
- **Fish Shell Completion**:
  - Added `AutoComplete.fish()` generating declarative `complete -c` scripts for fish shell.
  - Added `--shell=fish` option to `generate-completion` and the standalone `AutoComplete` CLI.

### Changed
- **Hidden Argument Groups**: Groups marked `hidden` are flattened directly onto their parent command with `hidden = true`, avoiding empty synopsis brackets (`"[]"`) or orphaned headings.

---

## [v4.8.0] - 2026-08-15

### Added
- Initial implementation of `picocli-spec` DSL and JSON schema reader/writer.
- Publishing of versioned JSON schemas and Javadoc to GitHub Pages.

---

## [v4.7.8] - 2026-06-01

### Added
- Initial fish shell autocompletion support (`AutoComplete.fish()`).
- Self-hosted release workflow publishing artifacts to GitHub Pages and GitHub Releases.
