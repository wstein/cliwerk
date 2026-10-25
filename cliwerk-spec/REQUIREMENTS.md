# cliwerk-spec Requirements

This document captures the functional, non-functional, and domain requirements for the `cliwerk-spec` module in accordance with Spec-Driven Development (SDD).

---

## 1. Motivation and Problem Statement

Building rich command-line interfaces for external tools or sub-processes often requires writing boilerplate Java classes with annotations (`@Command`, `@Option`, `@Parameters`).

When providing an integrated or proxy "uber" CLI that wraps, delegates to, or configures third-party tools (e.g. `flix`, package managers, compilers), defining annotated Java classes for every tool's commands and options introduces unnecessary coupling, maintenance overhead, and compilation weight.

`cliwerk-spec` enables defining complete picocli `CommandSpec` models from a lightweight DSL or standard JSON without annotated Java classes, with full support for picocli's parsing, validation, `--help` rendering, and autocompletion generation.

---

## 2. Functional Requirements

### FR-1: Lightweight DSL Parsing
- **FR-1.1**: Parse a human-friendly text DSL (`.cliwerk`) into a fully populated `CommandSpec` instance via `CommandSpecDsl.parse(String)`.
- **FR-1.2**: Support commands, subcommands (nested to arbitrary depth), options (short, long, multiple names), positional parameters, and descriptions.
- **FR-1.3**: Support option/positional attributes: `type`, `default`, `required`, `arity`, `inherit` (`ScopeType.INHERIT`), `hidden`, `usageHelp`, `versionHelp`, and `helpSection`.
- **FR-1.4**: Preserve multi-line descriptions by splitting on newline escapes (`\n`) and carriage returns into multi-element string arrays.
- **FR-1.5**: Support `mixinStandardHelpOptions` directive inside command blocks, mixing in standard `-h`/`--help` and `-V`/`--version` options.

### FR-2: JSON Specification Support
- **FR-2.1**: Parse JSON conforming to `command-spec.schema.json` into a `CommandSpec` via `CommandSpecJson.read(String)`.
- **FR-2.2**: Enforce that the document root is a valid JSON object.
- **FR-2.3**: Forbid nested `definitions` blocks in subcommand objects; reject with an informative `IllegalArgumentException`.
- **FR-2.4**: Support `mixinStandardHelpOptions: true` in command objects.

### FR-3: Serialization and Round-Trip Fidelity
- **FR-3.1**: Serialize any `CommandSpec` (programmatic, spec-built, or annotation-based) into JSON via `CommandSpecJson.write(CommandSpec)`.
- **FR-3.2**: Serialize any `CommandSpec` into DSL text via `CommandSpecDsl.write(CommandSpec)`.
- **FR-3.3**: Ensure round-trip fidelity: `DSL -> CommandSpec -> DSL` and `JSON -> CommandSpec -> JSON` produce semantically equivalent models and descriptions.
- **FR-3.4**: Exclude inherited, group-contained, and standard help mixin options from flat option arrays during serialization to prevent duplicate emission, emitting `mixinStandardHelpOptions` when enabled.

### FR-4: Spec Composition and Merging
- **FR-4.1**: Attach one or more imported `CommandSpec` instances to a host "uber" command as subcommands via `CommandSpecMerger.merge(CommandSpec uber, CommandSpec... imported)`.
- **FR-4.2**: Support batch merging of collections via `CommandSpecMerger.mergeAll(CommandSpec uber, Collection<CommandSpec> imported)`.
- **FR-4.3**: Reject imported specs lacking a name or colliding with an existing subcommand name on the host command.
- **FR-4.4**: Run ambiguity validation on the merged composite model before returning.

### FR-5: Definition Reuse and Bundles
- **FR-5.1**: Support a top-level `definitions` block in both DSL and JSON containing reusable `options`, `positionalParams`, and `bundles`.
- **FR-5.2**: Allow commands and groups to reference defined options or positionals by name without repeating types, arity, or descriptions.
- **FR-5.3**: Support `bundle` definitions grouping options, positionals, and arg-groups, expandable via `use <bundleName>`.
- **FR-5.4**: Support bundle composition (`bundle` using an earlier `bundle`).

### FR-6: Argument Grouping and Section Tagging
- **FR-6.1**: Support `group` definitions (both `exclusive` and `cooperative`, with optional `multiplicity` and `heading`).
- **FR-6.2**: Support `hidden` groups that flatten members into the parent command with `hidden = true` to avoid empty synopsis brackets or orphaned headings.
- **FR-6.3**: Support `helpSection` tagging on groups and options for on-demand help triggers (excluding flags from default `--help` while preserving them in shell completion).

### FR-7: Standard Type Vocabulary
- **FR-7.1**: Provide a standard type vocabulary in `ArgTypes`: `String`, `boolean`, `int`, `long`, `double`, `File`, `Path`, `URI`, `URL`, `BigDecimal`, and `BigInteger`.
- **FR-7.2**: Support array suffixes (e.g. `File[]`, `String[]`, `Path[]`) for multi-value arguments.
- **FR-7.3**: Enforce synchronization between `ArgTypes` and `command-spec.schema.json`'s type pattern.

---

## 3. Non-Functional Requirements

### NFR-1: Zero External Dependencies
- The module must have zero runtime dependencies beyond picocli core.
- JSON parsing must be implemented with a zero-dependency minimal parser (`cliwerk.spec.json.Json`).

### NFR-2: Target Compatibility
- Target Java 11 bytecode compatibility (`sourceCompatibility = 11`, `targetCompatibility = 11`).
- Build and run verified across LTS JDKs: 11, 17, and 21.

### NFR-3: Fail-Fast Ambiguity Validation
- Automatically validate specs during parse and merge against parsing ambiguities (e.g. default values conflicting with subcommand names) using `SpecValidator`.

### NFR-4: Human Ergonomics
- The DSL must be plain-text authorable in standard text editors without special plugins or build steps.
- Line comments (`//`) and whitespace must be supported.

---

## 4. Out of Scope

- **Execution dispatch**: The host application supplies execution logic (`Runnable`, `Callable`, or `IExecutionStrategy`).
- **Arbitrary user types**: Types requiring custom `ITypeConverter` registrations not built into picocli core are out of scope for the standard schema vocabulary.
- **Inter-file mixins**: Shorthand syntax across multiple files is out of scope; composition is done via `CommandSpecMerger`.
