# cliwerk-spec Design

This document details the architectural design, formal grammar, data structures, and component interactions of the `cliwerk-spec` module.

---

## 1. Architectural Overview

`cliwerk-spec` implements a two-way, schema-validated pipeline converting between human-authored DSL text, structured JSON documents, and picocli's internal `CommandSpec` object graph:

```
DSL Text (.cliwerk)                 JSON Document (.json)
        │                                    │
        ▼                                    ▼
 CommandSpecDsl.parse()              CommandSpecJson.read()
        │                                    │
        └──────────────┬─────────────────────┘
                       ▼
             CommandSpec (picocli core)
                       │
       ┌───────────────┼────────────────────┐
       ▼               ▼                    ▼
CommandSpecMerger   CommandSpecDsl.write()  CommandSpecJson.write()
(attach to uber)       │                    │
                       ▼                    ▼
                   DSL Text             JSON Document
```

### Component Breakdown

| Component | Responsibility |
|---|---|
| `CommandSpecDsl` | Lexer, recursive-descent parser, and DSL serializer. Converts between `.cliwerk` DSL and `CommandSpec`. |
| `CommandSpecJson` | Reads JSON maps into `CommandSpec` trees and serializes `CommandSpec` trees to formatted JSON. |
| `cliwerk.spec.json.Json` | Lightweight zero-dependency tokenizer and parser producing nested `Map<String, Object>` and `List<Object>`. |
| `CommandSpecMerger` | Attaches one or more imported specs as subcommands onto a host uber `CommandSpec`, with varargs and collection (`mergeAll`) support. |
| `SpecValidator` | Evaluates a `CommandSpec` tree for known CLI parsing ambiguities (such as an option default value colliding with a subcommand name). |
| `ArgTypes` | Bidirectional mapping between string type names (`"String"`, `"int"`, `"File"`, `"Path"`, etc.) and Java `Class<?>` instances. |

### Architectural Decision Records (ADRs)
- [ADR-001: Custom Minimal JSON Parser](../docs/adr/ADR-001-custom-json-parser.md)
- [ADR-002: Dual DSL and JSON Format Strategy](../docs/adr/ADR-002-dsl-and-json-dual-format.md)
- [ADR-003: Two-Module Split](../docs/adr/ADR-003-two-module-split.md)

---

## 2. DSL Grammar and Language Specification

### Grammar (EBNF)

```ebnf
spec        := definitions? command
definitions := 'definitions' '{' ( option | positional | bundle )* '}'
bundle      := 'bundle' name '{' ( option | positional | group | use )* '}'
command     := 'command' name ['helpSection' '=' value] [string] '{' member* '}'
member      := option | positional | group | section | use | command | 'mixinStandardHelpOptions'
section     := 'section' name [string] sectionAttr*
sectionAttr := 'heading' '=' value
             | 'emptyMessage' '=' value
             | 'notice' '=' value
group       := 'group' ('exclusive' | 'cooperative') ['hidden'] ['helpSection' '=' value] ['multiplicity' '=' value] [string]
               '{' ( option | positional | group | use )* '}'
use         := 'use' name
option      := 'option' name (',' name)* ( ':' type [string] attr* )?
positional  := 'positional' name ( ':' type [string] attr* )?
attr        := 'default' '=' value
             | 'required'
             | 'arity' '=' value
             | 'inherit'
             | 'hidden'
             | 'helpSection' '=' value
             | 'usageHelp'
             | 'versionHelp'
name, type,
value       := word
string      := '"' ... '"'     (* supports escapes: \", \\, \n, \t *)
word        := [^\s{}:,="]+
```

### Lexer and Syntax Rules
- **Line comments**: `//` continues to end-of-line (ignored outside strings).
- **Strings and Multi-line descriptions**: Quoted strings (`"..."`) can contain escape sequences (`\n`, `\t`, `\"`, `\\`) or literal newlines. During parsing, multi-line descriptions are split on `\n` into a `String[]` description. During serialization (`write`), `String[]` elements are joined with `\n`.
- **References vs Definitions**: An `option` or `positional` inside a command body that omits `: <type>` is an identifier reference resolving to an entry in the top-level `definitions` block.
- **Positional Param Labels**: A positional's identifier (e.g. `files`) automatically forms the angle-bracket parameter label (`<files>`), conforming to picocli conventions.

---

## 3. Definitions and Reusable Bundles

CLIs often share arguments across multiple commands or subcommands:
1. **Single Argument Definition**: Described in `definitions { option --json : boolean "..." }` and referenced in commands via `option --json`.
2. **Bundle (`bundle` / `use`)**: Groups a collection of options, positionals, or groups under a name, expanded into commands with `use <bundleName>`. Bundles can also `use` earlier bundles.
3. **Instance Independence**: References and `use` directives instantiate independent `OptionSpec` / `PositionalParamSpec` objects for each command. No mutable runtime state is shared between commands.

---

## 4. Groups, Hidden Flags, and Help Sections

### ArgGroups
- `group exclusive`: At most one member matched (picocli default).
- `group cooperative`: Members matched together; combined with `multiplicity=1` to enforce that a set of flags is supplied together or not at all.
- Group arguments are registered with the parent `CommandSpec` through picocli's `addArgGroup`, and omitted from top-level flat option lists in JSON/DSL to prevent duplication.

### Hidden Groups
A group marked `hidden` is flattened: its constituent members are registered directly on the enclosing command with `hidden = true`, and the group itself is not created as an `ArgGroupSpec`. This prevents picocli's usage help renderer from displaying empty synopsis brackets (`"[]"`) or orphaned section headings.

### Help Section Tagging (`helpSection`)
Options, groups, and subcommands can be tagged with `helpSection="<sectionName>"`:
- Flags and tagged subcommands are excluded from default `--help`.
- Flags and subcommands remain visible in shell autocompletion (`bash`, `fish`, `zsh`).
- An option marked with `helpSection="<name>"` acts as an on-demand help trigger: invoking it prints only the tagged help section (options, groups, and subcommands) and exits cleanly.

---

## 5. JSON Schema and Field Reference

The schema is formally maintained in [command-spec.schema.json](src/main/resources/cliwerk/spec/command-spec.schema.json).

### Command Object

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | inside `subcommands` | Command name. Optional at root. |
| `description` | `string[]` | no | Array of description lines. |
| `helpSection` | `string` | no | Help section tag (e.g. `"experimental"`). Excluded from standard help, rendered on demand. |
| `mixinStandardHelpOptions` | `boolean` | no | Sets whether standard help options (`-h`, `--help`, `-V`, `--version`) should be mixed in (`spec.mixinStandardHelpOptions(true)`). Defaults to `false`. |
| `options` | `(option \| string)[]` | no | Inlined options or references to `definitions.options`. |
| `positionalParams` | `(positionalParam \| string)[]` | no | Inlined positionals or references to `definitions.positionalParams`. |
| `argGroups` | `argGroup[]` | no | Argument groups. |
| `use` | `string[]` | no | Bundles from `definitions.bundles` to expand. |
| `subcommands` | `command[]` | no | Nested subcommand definitions. |
| `definitions` | `object` | no | Document root only: `{ options, positionalParams, bundles }`. Subcommands must not contain `definitions`. |

### Option Object

| Field | Type | Default | Description |
|---|---|---|---|
| `names` | `string[]` | required | Option switches, e.g. `["-v", "--verbose"]`. |
| `type` | `string` | `"String"` | Name from `ArgTypes`, optionally suffixed with `[]`. |
| `description` | `string[]` | `[]` | Usage-help text lines. |
| `defaultValue` | `string` | `null` | Default argument value. |
| `required` | `boolean` | `false` | Whether the option is required. |
| `arity` | `string` | inferred | Arity range, e.g. `"0"`, `"1"`, `"0..*"`. |
| `usageHelp` | `boolean` | `false` | Built-in usage help trigger. |
| `versionHelp` | `boolean` | `false` | Built-in version help trigger. |
| `hidden` | `boolean` | `false` | Suppresses option from default help. |
| `helpSection` | `string` | `null` | On-demand help section name. |
| `scope` | `string` | `"local"` | Set to `"inherit"` for `ScopeType.INHERIT`. |

### PositionalParam Object
Identical to Option, but replaces `names` with optional `paramLabel` (`string`, defaults to `"<PARAM>"`), and excludes `usageHelp`/`versionHelp`.

---

## 6. Type Vocabulary (`ArgTypes`)

Supported types are verified and mapped to standard Java classes:
- `String` -> `java.lang.String`
- `boolean` -> `boolean.class`
- `int` -> `int.class`
- `long` -> `long.class`
- `double` -> `double.class`
- `File` -> `java.io.File`
- `Path` -> `java.nio.file.Path`
- `URI` -> `java.net.URI`
- `URL` -> `java.net.URL`
- `BigDecimal` -> `java.math.BigDecimal`
- `BigInteger` -> `java.math.BigInteger`

Appending `[]` (e.g. `Path[]`, `File[]`) produces the respective Java array type.

---

## 7. Ambiguity Validation (`SpecValidator`)

During parsing or merging, `SpecValidator` checks:
1. **Subcommand / Default-Value Collision**: If an option defines a `defaultValue` identical to a sibling subcommand's name, picocli fails during evaluation. `SpecValidator` detects and rejects this at spec load time.
2. **Positional Name Clashes**: Validates that positional configurations do not introduce unparseable command structures.
