# cliwerk

[![CI](https://github.com/wstein/cliwerk/actions/workflows/ci.yml/badge.svg)](https://github.com/wstein/cliwerk/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/release/wstein/cliwerk.svg)](https://github.com/wstein/cliwerk/releases)

**cliwerk** is an independent, long-lived fork of [picocli](https://github.com/remkop/picocli)
by Remko Popma, maintained by [wstein](https://github.com/wstein) under the
[Apache License 2.0](LICENSE) (see also [NOTICE](NOTICE)).

> **cliwerk is not affiliated with or endorsed by the picocli project.**
> "picocli" refers to the upstream project; cliwerk releases are not picocli releases.
> **For everything about picocli itself** — features, user manual, examples, articles —
> see the [upstream README](https://github.com/remkop/picocli#readme) and [picocli.info](https://picocli.info).

Relationship to upstream, branch model and naming rules: [docs/FORK.adoc](docs/FORK.adoc).

### Transition note: package and coordinates are unchanged for now

The project name changed to cliwerk, but the code did not (yet):

- The Java package is still `picocli` (`picocli.CommandLine`, `@Command`, `@Option`, ...);
  the library API described in the upstream documentation applies unchanged.
- One artifact is published, `io.github.wstein:picocli` (artifactId `picocli`, under this fork's own groupId —
  distinct from upstream's `info.picocli`). There is no separate `picocli-spec` artifact:
  the `picocli-spec` classes are bundled into this artifact.
- The repository has been renamed from `wstein/picocli` to `wstein/cliwerk` (GitHub redirects the old repository URLs),
  but the GitHub Pages links below (Maven repo, Javadoc, JSON Schema) still use `wstein.github.io/picocli`
  and will be updated in a separate change.

### Migration outlook

The Java package stays `picocli`: cliwerk is meant as a drop-in replacement for upstream picocli,
so it cannot be on the same classpath as `info.picocli:picocli`.
The new Maven coordinates (`io.github.wstein:cliwerk`) and the versioning scheme are decided but not yet implemented;
see the decisions in [docs/FORK.adoc](docs/FORK.adoc). No date has been decided.
The releases 4.7.8 to 4.9.4 were cliwerk's own releases, not upstream picocli releases. Future versions will be
based on upstream 4.7.7 (`4.7.7.1`, ...), which Maven and Gradle sort below 4.9.4, so pin the cliwerk version explicitly. Until then, existing
coordinates and imports keep working; any change will be described in the [CHANGELOG](CHANGELOG.md)
before it is released.

---

## What cliwerk adds on top of upstream picocli

| Feature | Status | Upstream PR |
| --- | --- | --- |
| **Fish shell completion** — `AutoComplete.fish()` generates declarative `complete -c` scripts (further fixes and feature parity in progress) | ✅ In cliwerk | [remkop/picocli#2463](https://github.com/remkop/picocli/pull/2463) |
| **`picocli-spec`** — build a `CommandSpec` from a DSL or JSON without any annotated Java class | ✅ In cliwerk | Not yet proposed |
| **`picocli-spec-tool`** — CLI tool to preview, validate, convert, and generate completions/man pages for spec files | ✅ In cliwerk | Not yet proposed |
| **`helpSection` on `@Option` / `@ArgGroup`** — on-demand help sections (e.g. `--Xhelp` shows experimental flags) | ✅ In cliwerk | Not yet proposed |

---

## Download

cliwerk currently publishes under `io.github.wstein:picocli` (distinct from upstream's `info.picocli:picocli`;
see the transition note above). Releases are cut by pushing a `v<version>` tag: the
[release workflow](.github/workflows/release.yml) builds the jars, creates a GitHub Release and
publishes the flat Maven repository, Javadoc and JSON Schema to GitHub Pages.

### Gradle

```groovy
repositories {
    maven { url 'https://wstein.github.io/picocli/maven' }
}
dependencies {
    implementation 'io.github.wstein:picocli:<version>'
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>wstein-cliwerk</id>
    <url>https://wstein.github.io/picocli/maven</url>
  </repository>
</repositories>
<dependency>
  <groupId>io.github.wstein</groupId>
  <artifactId>picocli</artifactId>
  <version>VERSION</version>
</dependency>
```

Jars are also attached directly to [GitHub Releases](https://github.com/wstein/cliwerk/releases).

**Javadoc and JSON Schema** are hosted on GitHub Pages —
see the [landing page](https://wstein.github.io/picocli/) for all links.

---

## picocli-spec

[`picocli-spec`](picocli-spec/README.md) builds a picocli `CommandSpec` from a small DSL or JSON,
exports any `CommandSpec` back to JSON, and merges specs into a host CLI as subcommands —
all without an annotated Java class.

Intended use-case: give a beautiful, help-and-completion-enabled picocli front end to a
third-party tool whose own CLI isn't human-friendly.

```
// flix-0.60.0.picocli  (DSL)
command flix "The Flix Programming Language 0.60.0" {
  option --help    : boolean "prints this usage information." usageHelp
  option --version : boolean "prints the version number."    versionHelp
  option --listen  : String  "starts the socket server and listens on the given port."
  command init  "creates a new project in the current directory." { ... }
  command build "builds the current project." { ... }
  ...
}
```

```java
CommandSpec flixSpec = CommandSpecDsl.parse(Files.readString(Path.of("flix-0.60.0.picocli")));
CommandSpecMerger.merge(myUberSpec, flixSpec);
```

→ [`picocli-spec` README](picocli-spec/README.md) ·
[JSON Schema](https://wstein.github.io/picocli/spec/schema/command-spec.schema.json) ·
[Javadoc](https://wstein.github.io/picocli/apidocs/picocli-spec/)

### Gradle

```groovy
dependencies {
    implementation 'io.github.wstein:picocli-spec:<version>'
}
```

---

## picocli-spec-tool

[`picocli-spec-tool`](picocli-spec-tool/README.md) is a self-contained fat-jar CLI for working
with `.picocli` / `.json` spec files — no Java code required.

```
java -jar picocli-spec-tool-<version>-all.jar <subcommand> <spec-file>
```

| Subcommand | What it does |
| --- | --- |
| `preview` | Prints the full usage help for every command in the spec |
| `validate` | Load-only pass/fail — good for pre-commit hooks and CI |
| `completion` | Generates a bash/zsh or fish completion script |
| `manpage` | Generates AsciiDoc man pages via picocli-codegen |
| `convert` | Converts between DSL and JSON formats |

→ [`picocli-spec-tool` README](picocli-spec-tool/README.md)

---

## Upstream picocli

For everything about picocli itself — annotations, programmatic API, user manual, GraalVM,
Spring/Micronaut/Quarkus integration, community articles — see:

**[remkop/picocli on GitHub](https://github.com/remkop/picocli#readme)**
**[picocli.info](https://picocli.info)** (user manual)
**[Quick Guide](https://picocli.info/quick-guide.html)**

To use the latest **upstream** picocli release instead of cliwerk:

```groovy
// Gradle
implementation 'info.picocli:picocli:4.7.7'
```

```xml
<!-- Maven -->
<dependency>
  <groupId>info.picocli</groupId>
  <artifactId>picocli</artifactId>
  <version>4.7.7</version>
</dependency>
```
