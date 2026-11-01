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
- One artifact is published, `io.github.wstein:cliwerk` (this fork's own groupId and artifactId —
  distinct from upstream's `info.picocli:picocli`). There is no separate `cliwerk-spec` artifact:
  the `cliwerk-spec` classes are bundled into this artifact.
- The repository has been renamed from `wstein/picocli` to `wstein/cliwerk` (GitHub redirects the old repository URLs),
  and the GitHub Pages links below (Maven repo, Javadoc, JSON Schema) use `wstein.github.io/cliwerk`.

### Migration outlook

The Java package stays `picocli`: cliwerk is meant as a drop-in replacement for upstream picocli,
so it cannot be on the same classpath as `info.picocli:picocli`.
The Maven coordinates are `io.github.wstein:cliwerk` (see [docs/FORK.adoc](docs/FORK.adoc)).
A rename of the Java package is deliberately deferred; no date has been decided.
Versions are `<upstream x.y.z>.<fork revision>`: the current version is `4.7.7.1`, based on upstream picocli 4.7.7.
The earlier cliwerk releases 4.7.8 to 4.9.4 were never upstream picocli releases; they have been withdrawn and deleted,
and under the artifactId `cliwerk` there are no versions that compete with `4.7.7.x`.
File names: the jars attached to GitHub Releases and built into `build/libs` are `cliwerk-<version>*.jar`,
the same name as in the Maven repository.

---

## What cliwerk adds on top of upstream picocli

| Feature | Status | Upstream PR |
| --- | --- | --- |
| **Fish shell completion** — `AutoComplete.fish()` generates declarative `complete -c` scripts (further fixes and feature parity in progress) | ✅ In cliwerk | [remkop/picocli#2463](https://github.com/remkop/picocli/pull/2463) |
| **`cliwerk-spec`** — build a `CommandSpec` from a DSL or JSON without any annotated Java class | ✅ In cliwerk | Not yet proposed |
| **`cliwerk-spec-tool`** — CLI tool to preview, validate, convert, and generate completions/man pages for spec files | ✅ In cliwerk | Not yet proposed |
| **`helpSection` on `@Option` / `@ArgGroup`** — on-demand help sections (e.g. `--Xhelp` shows experimental flags) | ✅ In cliwerk | Not yet proposed |

---

## Download

cliwerk publishes as `io.github.wstein:cliwerk` (distinct from upstream's `info.picocli:picocli`;
see the transition note above). The first release is `4.7.7.1`. Releases are cut by pushing a `v<version>` tag: the
[release workflow](.github/workflows/release.yml) builds the jars, creates a GitHub Release and
publishes the flat Maven repository, Javadoc and JSON Schema to GitHub Pages.

### Gradle

```groovy
repositories {
    maven { url 'https://wstein.github.io/cliwerk/maven' }
}
dependencies {
    implementation 'io.github.wstein:cliwerk:4.7.7.1'
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>wstein-cliwerk</id>
    <url>https://wstein.github.io/cliwerk/maven</url>
  </repository>
</repositories>
<dependency>
  <groupId>io.github.wstein</groupId>
  <artifactId>cliwerk</artifactId>
  <version>4.7.7.1</version>
</dependency>
```

Jars are also attached directly to [GitHub Releases](https://github.com/wstein/cliwerk/releases).

**Javadoc and JSON Schema** are hosted on GitHub Pages —
see the [landing page](https://wstein.github.io/cliwerk/) for all links.

---

## cliwerk-spec

[`cliwerk-spec`](cliwerk-spec/README.md) builds a picocli `CommandSpec` from a small DSL or JSON,
exports any `CommandSpec` back to JSON, and merges specs into a host CLI as subcommands —
all without an annotated Java class.

Intended use-case: give a beautiful, help-and-completion-enabled picocli front end to a
third-party tool whose own CLI isn't human-friendly.

```
// flix-0.60.0.cliwerk  (DSL)
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
CommandSpec flixSpec = CommandSpecDsl.parse(Files.readString(Path.of("flix-0.60.0.cliwerk")));
CommandSpecMerger.merge(myUberSpec, flixSpec);
```

→ [`cliwerk-spec` README](cliwerk-spec/README.md) ·
[JSON Schema](https://wstein.github.io/cliwerk/spec/schema/command-spec.schema.json) ·
[Javadoc](https://wstein.github.io/cliwerk/apidocs/cliwerk-spec/)

### Gradle

The spec classes are bundled into the root artifact; no separate dependency is needed:

```groovy
dependencies {
    implementation 'io.github.wstein:cliwerk:4.7.7.1'
}
```

---

## cliwerk-spec-tool

[`cliwerk-spec-tool`](cliwerk-spec-tool/README.md) is a self-contained fat-jar CLI for working
with `.cliwerk` / `.json` spec files — no Java code required.

```
java -jar cliwerk-spec-tool-<version>-all.jar <subcommand> <spec-file>
```

| Subcommand | What it does |
| --- | --- |
| `preview` | Prints the full usage help for every command in the spec |
| `validate` | Load-only pass/fail — good for pre-commit hooks and CI |
| `completion` | Generates a bash/zsh, fish or PowerShell completion script |
| `manpage` | Generates AsciiDoc man pages via picocli-codegen |
| `convert` | Converts between DSL and JSON formats |

→ [`cliwerk-spec-tool` README](cliwerk-spec-tool/README.md)

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
