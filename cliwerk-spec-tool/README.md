# cliwerk-spec-tool

A cliwerk command line tool for working with [`cliwerk-spec`](../cliwerk-spec) DSL/JSON
spec files: preview the CLI a spec produces, generate a shell completion script or AsciiDoc man
pages for it, or just check that it loads — all without writing a line of Java.

Built the ordinary way, with picocli's own annotations (`@Command`/`@Option`/`@Parameters`).
Unlike the specs it operates on, this outer tool is not itself described by a cliwerk-spec
file — a spec-authoring tool has no reason to avoid the annotation processor it's built with.

## Building

```
$ ./gradlew :cliwerk-spec-tool:build
```

produces a self-contained, directly runnable jar at
`cliwerk-spec-tool/build/libs/cliwerk-spec-tool-<version>-all.jar`, via the
[Shadow](https://gradleup.com/shadow/) plugin — it bundles picocli, picocli-codegen, and
cliwerk-spec together with this module's own classes, so nothing else needs to be on the
classpath. (The plain, non-`-all` jar next to it in the same directory is this module's own
classes only, same as any other module here — not runnable by itself.)

Shadow is pinned to `8.3.9` and applied *conditionally*, only when the JVM running Gradle is Java
8 or newer: later Shadow releases require Java 11 or (from `9.x`) Java 17 and Gradle 9 just to run
the plugin, which this repo's own CI matrix — building the whole project under Java 6/7 too —
can't assume. Building under Java 6/7 still works; it just skips `shadowJar` (no `-all` jar), the
same way this module has always still compiled there without producing anything Java 8-specific.

## Usage

```
$ java -jar cliwerk-spec-tool-<version>-all.jar <subcommand> ...
```

| Subcommand | What it does |
|---|---|
| `preview <spec-file>` | Prints the usage help message for the spec's root command and, recursively, every subcommand — see exactly what CLI the spec produces. `--ansi=on\|off\|auto` controls color. |
| `completion <spec-file>` | Generates a bash/zsh, fish or PowerShell completion script, via picocli's own `AutoComplete`. `-s, --shell=bash\|fish\|powershell` picks the target shell (default: `bash`); `--name` overrides the script's command name (default: the spec's own root command name); `--output <file>` writes to a file instead of stdout. |
| `manpage <spec-file>` | Generates AsciiDoc man pages for the spec and every subcommand, via picocli-codegen's `ManPageGenerator`. `--outdir <dir>` sets the output directory (default: the current directory). Existing man pages are never replaced unless `--overwrite` is given; without it the command writes nothing at all if any of them is already there. |
| `validate <spec-file>` | Loads the spec and reports `OK` or a one-line error — nothing is generated. Loading already runs every DSL/JSON parsing rule and `SpecValidator`'s checks, so this is just a clear pass/fail wrapper around that for a shell workflow (e.g. a pre-commit hook or CI step). |
| `convert <spec-file>` | Converts a spec file between DSL and JSON formats. `-t, --to=dsl\|json` specifies the target format (default: deduced from `--output` extension or the opposite of the input format); `-o, --output <file>` writes to a file instead of stdout. |

Every subcommand accepts either a `.cliwerk` (DSL) or a `.json` file — the extension picks the
reader, same as `SpecLoader` does internally.

### Example

```
$ java -jar cliwerk-spec-tool-<version>-all.jar preview ../cliwerk-spec/examples/flix-0.60.0.cliwerk
Usage: flix [--help] [--version] [--listen=PARAM] [COMMAND]
The Flix Programming Language 0.60.0
      --help           prints this usage information.
      --listen=PARAM   starts the socket server and listens on the given port.
      --version        prints the version number.
Commands:
  init          creates a new project in the current directory.
  check         checks the current project for errors.
  ...

Usage: flix init [--yes]
creates a new project in the current directory.
      --yes   automatically answer yes to all prompts.

Usage: flix check [--explain] [--json] [--no-install] [--Xhelp]
                  [--github-token=PARAM] [--threads=PARAM] [<files>...]
...
```

## Why a separate module

`cliwerk-spec` itself has exactly one dependency, `picocli` core (`api rootProject`), matching
picocli's own zero-runtime-dependency philosophy. `manpage` needs `picocli-codegen`, a real
dependency edge this tool is happy to take on but the core spec-building library shouldn't have
to. Keeping the renderer in its own module means authoring/merging specs never drags in
`picocli-codegen` (or this tool's own transitive footprint) for consumers who only need that.

## Limitations

- `completion` supports `bash` (and `zsh` via `bashcompinit`) and `fish`; PowerShell completion isn't
  wired up.
- No execution wiring, same as `cliwerk-spec` itself: this tool only renders a spec, it never
  runs the CLI it describes.

See `SpecLoaderTest`, `PreviewTest`, `CompletionTest`, `ManpageTest`, `ValidateTest`, and
`SpecToolAppTest` (the last one exercises every subcommand end-to-end through
`CommandLine#execute`, the same way a real invocation would) for runnable examples.
