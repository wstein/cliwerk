# cliwerk-spec

Build a picocli `CommandSpec` from JSON or from a lightweight DSL, export any `CommandSpec` back to
JSON, and merge one or more such specs into a host ("uber") CLI as subcommands — without an
annotated Java class.

- **Requirements Specification:** [cliwerk-spec/REQUIREMENTS.md](cliwerk-spec/REQUIREMENTS.md)
- **Detailed Design & Grammar:** [cliwerk-spec/DESIGN.md](cliwerk-spec/DESIGN.md)
- **Architecture Decision Records:** [docs/adr/ADR-001-custom-json-parser.md](docs/adr/ADR-001-custom-json-parser.md), [docs/adr/ADR-002-dsl-and-json-dual-format.md](docs/adr/ADR-002-dsl-and-json-dual-format.md), [docs/adr/ADR-003-two-module-split.md](docs/adr/ADR-003-two-module-split.md)
- **JSON Schema:** [cliwerk-spec/src/main/resources/cliwerk/spec/command-spec.schema.json](cliwerk-spec/src/main/resources/cliwerk/spec/command-spec.schema.json)
  (published at <https://wstein.github.io/cliwerk/spec/schema/command-spec.schema.json>)
- **Javadoc:** <https://wstein.github.io/cliwerk/apidocs/cliwerk-spec/>

---

## Quick Start

### 1. Define a Command in DSL (`.cliwerk`)

```
command flix "The Flix programming language" {
  option -v, --verbose : boolean "Enable verbose output"
  option -o, --output : Path "Output directory" default=out
  option -t, --target : String "Compilation target" required arity=1

  command build "Compile the project" {
    option --release : boolean "Optimize for release"
    positional files : Path "Input files to compile" arity=0..*
  }
}
```

Parse into a picocli `CommandSpec`:

```java
CommandSpec flixSpec = CommandSpecDsl.parse(dslText);
```

### 2. Or Define in JSON (`.json`)

```json
{
  "name": "flix",
  "description": ["The Flix programming language"],
  "options": [
    { "names": ["-v", "--verbose"], "type": "boolean", "description": ["Enable verbose output"] },
    { "names": ["-o", "--output"], "type": "Path", "defaultValue": "out" }
  ],
  "subcommands": [
    {
      "name": "build",
      "description": ["Compile the project"],
      "options": [
        { "names": ["--release"], "type": "boolean", "description": ["Optimize for release"] }
      ],
      "positionalParams": [
        { "paramLabel": "<files>", "type": "Path", "arity": "0..*" }
      ]
    }
  ]
}
```

Read into a `CommandSpec` or export back to JSON:

```java
CommandSpec spec = CommandSpecJson.read(jsonText);
String exportedJson = CommandSpecJson.write(spec);
```

### 3. Standard Help Options

Both the DSL and JSON support picocli's `@Command(mixinStandardHelpOptions = true)` shorthand to automatically mix in standard `-h`, `--help` and `-V`, `--version` options:

```
command mytool {
  mixinStandardHelpOptions
  option -v, --verbose : boolean
}
```

Or in JSON:

```json
{
  "name": "mytool",
  "mixinStandardHelpOptions": true
}
```

---

## Composing into a Host CLI

Attach spec-defined command trees to your application's root command:

```java
CommandSpec uber = new CommandLine(new MyUberCommand()).getCommandSpec();

// Varargs:
CommandSpecMerger.merge(uber, flixSpec, otherToolSpec);

// Or via Collection:
CommandSpecMerger.mergeAll(uber, specList);

new CommandLine(uber).execute(args);
```

Execution dispatch is handled by your host command (e.g. an `IExecutionStrategy` shelling out or delegating).

---

## Supported Argument Types

The standard vocabulary provided by `ArgTypes`:
`String`, `boolean`, `int`, `long`, `double`, `File`, `Path`, `URI`, `URL`, `BigDecimal`, `BigInteger`.
Any scalar type may be suffixed with `[]` (e.g. `Path[]`, `File[]`) for multi-value arguments.

---

## Tooling & Previews

The sibling [cliwerk-spec-tool](cliwerk-spec-tool) module provides the `cliwerk-spec` CLI utility to preview, validate, generate completions, and build man pages from spec files:

```bash
java -jar cliwerk-spec-tool-all.jar preview flix.cliwerk
java -jar cliwerk-spec-tool-all.jar completion flix.cliwerk --shell=bash
java -jar cliwerk-spec-tool-all.jar manpage flix.cliwerk --outdir=man/
java -jar cliwerk-spec-tool-all.jar convert flix.cliwerk --to-json
```

For complete grammar specifications, definition blocks, bundles, argument groups, and schema details, see [cliwerk-spec/DESIGN.md](cliwerk-spec/DESIGN.md).
