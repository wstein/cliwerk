# agents.md

**Canonical reference for all AI systems and code agents working on this repository.**

This file consolidates development guidance for Claude Code, GitHub Copilot, Cursor, Codex, and other AI tools.
It replaces project-specific conventions that would otherwise live in CLAUDE.md, .cursorrules, .copilot-instructions, or Codex configs.

**For AI systems**: Load this file as primary context before working with the codebase.
**For maintainers**: Update this single file when project conventions change; it automatically covers all AI integration points.

## Overview

This is **cliwerk** ([wstein/cliwerk](https://github.com/wstein/cliwerk)), an independent fork of [remkop/picocli](https://github.com/remkop/picocli) maintained by [wstein](https://github.com/wstein); it is not affiliated with or endorsed by the picocli project. Some enhancements are also proposed upstream. See [docs/FORK.adoc](docs/FORK.adoc) for branch model and naming rules (never call cliwerk releases "picocli"). The Java package `picocli` and the module/artifact names are unchanged during the transition. cliwerk adds:

- **Fish shell completion** via `AutoComplete.fish()` 
- **picocli-spec**: Build `CommandSpec` from DSL (`.picocli`) or JSON (`.json`) without annotated Java classes
- **picocli-spec-tool**: Standalone CLI tool to preview, validate, convert, and generate completions/man pages for spec files
- **On-demand help sections** (`helpSection` on `@Option` / `@ArgGroup`) for targeted help (e.g., `--Xhelp`)

Publishes as `io.github.wstein:picocli` (distinct from upstream's `info.picocli:picocli`) to Maven via GitHub Pages and GitHub Releases.

## Project Structure

### Core Modules

| Module | Purpose | Java Target |
|--------|---------|-------------|
| `picocli` (root) | Core annotation/programmatic API; also includes `AutoComplete` | Java 5 (1.8 compilation) |
| `picocli-codegen` | AsciiDoc man page, shell completion codegen | Java 6 |
| `picocli-spec` | DSL/JSON parser and writer for `CommandSpec` without annotations | Java 11 |
| `picocli-spec-tool` | Standalone `picospec` CLI (preview, validate, completion, manpage, convert) | Java 11 |

### Secondary Modules

- `picocli-groovy`, `picocli-shell-jline2/3`, `picocli-spring-boot-starter`: Integration modules for specific ecosystems
- `picocli-examples`: Demo applications
- `picocli-tests-java{5,6,7,8,9plus}`, `picocli-codegen-tests-java9plus`: Version-specific test suites

### Test Strategy

Tests are organized by Java version compatibility:
- `picocli-tests-java567`: Java 5/6/7 compatibility tests
- `picocli-tests-java8`: Java 8+ tests
- `picocli-tests-java9plus`: Java 9+ module system tests (uses `jvmArgs('--add-opens', ...)`)

Include `@ParameterizedTest` fixtures and round-trip fidelity tests (DSL → JSON → DSL).

## Build & Development Commands

### Core Build

```bash
# Clean build all modules
./gradlew clean build

# Build without tests (faster iteration)
./gradlew clean build -x test

# Run all tests across Java versions (CI matrix: 8, 11, 17, 21)
./gradlew test

# Run tests for a specific module
./gradlew :picocli-spec:test

# Run a single test class
./gradlew :picocli-spec:test --tests CommandSpecDslTest

# Run a specific test method
./gradlew :picocli-spec:test --tests CommandSpecDslTest.roundTripFidelity
```

### picocli-spec & Tool Specific

```bash
# Build picocli-spec-tool standalone fat jar (Shadow plugin)
./gradlew :picocli-spec-tool:shadowJar

# Run picospec CLI locally (after shadowJar)
java -jar picocli-spec-tool/build/libs/picocli-spec-tool-*-all.jar preview picocli-spec/examples/flix-0.60.0.picocli

# Generate Javadoc
./gradlew :picocli-spec:javadoc :javadoc

# Publish to local Maven repo (for testing consumption)
./gradlew publishToMavenLocal
```

### Code Quality

```bash
# Format and check code (if formatters are configured)
./gradlew spotlessApply  # if Spotless is configured
./gradlew checkstyleMain # if Checkstyle is configured

# Error-prone (Java 17+ only)
./gradlew compileJava  # errors from error-prone are shown during build
```

### Release & Publishing

```bash
# Set version (four parts <upstream x.y.z>.<fork revision>, e.g., 4.7.7.2; see docs/FORK.adoc) and release
# 1. Update projectVersion = "4.7.7.2" in dependencies.gradle AND CommandLine.VERSION (+ CommandLineTest.testVersion)
# 2. Push tag: git tag v4.7.7.2 && git push origin v4.7.7.2
# 3. CI will run release.yml → Maven artifact + Javadoc + schema to gh-pages
```

## Architecture & Design

### Zero-Dependency Philosophy

Both `picocli` core and `picocli-spec` follow a strict zero-runtime-dependency design:
- `picocli-spec` implements its own lightweight JSON parser (`picocli.spec.json.Json`, ~150 lines)
- No Jackson, Gson, or other JSON libraries
- Ensures minimal classpath footprint for consumers

**ADR References**:
- `docs/adr/ADR-001-custom-json-parser.md`: Why embedded JSON parser
- `docs/adr/ADR-002-dsl-and-json-dual-format.md`: DSL + JSON strategy
- `docs/adr/ADR-003-two-module-split.md`: Why picocli-spec and picocli-spec-tool are separate

### picocli-spec Key Design Patterns

1. **Dual Format (DSL & JSON)**:
   - DSL (`.picocli`): Human-friendly, concise syntax (EBNF in `DESIGN.md`)
   - JSON (`.json`): Machine-friendly, adheres to `command-spec.schema.json`
   - Lossless round-trip fidelity via `CommandSpecDsl.write()` and `CommandSpecJson.write()`

2. **Definition Reuse & Bundles**:
   - Top-level `definitions { }` blocks define reusable options/positionals
   - `bundle` declarations group options/positionals/arg-groups for composition
   - Used via `use <bundleName>` to avoid duplication

3. **Spec Composition** (`CommandSpecMerger`):
   - Merge external specs as subcommands onto a host CLI
   - Enables wrapping third-party tools (e.g., Flix) in picocli's CLI framework
   - Validates against ambiguities (e.g., option defaultValue colliding with subcommand name)

4. **Type System (`ArgTypes`)**:
   - Base vocabulary: `String`, `boolean`, `int`, `long`, `double`, `File`
   - Extended in v4.9.1: `Path`, `URI`, `URL`, `BigDecimal`, `BigInteger`
   - Array suffix support: `File[]`, `String[]`
   - Bidirectional mapping: Java type ↔ JSON schema representation

5. **Validation (`SpecValidator`)**:
   - Fails fast during parse or merge on ambiguities
   - Checks: name collisions, type mismatches, missing required fields
   - Entry point: `SpecValidator.validate(CommandSpec)`

### picocli-spec-tool Architecture

```
User invokes: picospec preview|validate|completion|manpage|convert <spec-file>
                    ↓
              SpecLoader (detects .picocli or .json by extension)
                    ↓
        CommandSpecDsl.parse() OR CommandSpecJson.read()
                    ↓
             CommandSpec (picocli model)
                    ↓
    [Preview → uses CommandLine#usage]
    [Validate → uses SpecValidator]
    [Completion → uses AutoComplete.bash() or AutoComplete.fish()]
    [Manpage → uses ManPageGenerator from picocli-codegen]
    [Convert → uses CommandSpecDsl.write() or CommandSpecJson.write()]
```

Shadow plugin bundles picocli, picocli-codegen, and picocli-spec into a single `-all.jar`.

### Help Sections Pattern

Annotated options with `@Option(helpSection = "EXPERIMENTAL")` are:
- Excluded from default `--help` output
- Renderable on-demand via tagged options (e.g., `--Xhelp` for section "EXPERIMENTAL")
- Discoverable in shell autocompletion (for power users)

## Testing Conventions

### Test Organization

- Place unit tests in the same module with `src/test/java`
- Fixture DSL and JSON files in `src/test/resources/picocli/spec/examples/` (or module-specific paths)
- Use `@ParameterizedTest` with `@CsvSource` or `@ValueSource` for matrix testing

### Round-Trip Fidelity

Critical for picocli-spec: verify DSL ↔ JSON bidirectional conversion:
```java
// Example pattern:
String dslText = "command foo { option --bar : String }";
CommandSpec spec1 = CommandSpecDsl.parse(dslText);
String json = CommandSpecJson.write(spec1);
CommandSpec spec2 = CommandSpecJson.read(json);
String dslText2 = CommandSpecDsl.write(spec2);
// Both roundtrips should be equivalent
```

### Test Files Location

Example fixture files:
- `picocli-spec/examples/flix-0.60.0.picocli` (Flix v0.60.0 example; multi-version progression in `flixw/src/assets/picocli/`)
- `picocli-spec/src/test/resources/picocli/spec/examples/` (test fixtures)

## Release Process

1. **Version Bump**: Update `projectVersion` in `dependencies.gradle` and `CommandLine.VERSION` (+ `CommandLineTest.testVersion`) to the four-part version (e.g., `4.7.7.2`; scheme in `docs/FORK.adoc`)
2. **Changelog**: Update `CHANGELOG.md` with Added/Changed/Fixed sections (Keep a Changelog format)
3. **Tag & Push**: `git tag v4.7.7.2 && git push origin v4.7.7.2` (the release workflow only accepts four-part tags)
4. **CI Automation** (`.github/workflows/release.yml`):
   - Runs full `./gradlew build` on Ubuntu/Java 17
   - Publishes jars to flat Maven repo (gh-pages/maven/)
   - Generates Javadoc and publishes to gh-pages/apidocs/
   - Publishes versioned JSON schema to gh-pages/spec/schema/{version}/
   - Creates GitHub Release with generated notes

## Java Version Support

**Current Policy** (v4.9.1):
- CI builds: LTS releases only (8, 11, 17, 21)
- macOS CI is non-blocking (flaky hosted runners)
- Dropped intermediate/non-LTS JDK versions and legacy Java 6/7 CI job

**Module Targets**:
- `picocli`: Java 5 bytecode (compiled with Java 8)
- `picocli-spec`, `picocli-spec-tool`: Java 11 bytecode

## Key Files & References

| File | Purpose |
|------|---------|
| `build.gradle` | Root build config; multi-module orchestration |
| `settings.gradle` | Module inclusion (conditional on Java version) |
| `dependencies.gradle` | Centralized dependency versions |
| `picocli-spec/README.md` | Quick start for DSL/JSON usage |
| `picocli-spec/REQUIREMENTS.md` | Functional requirements (FR-1–FR-7, NFR-1–NFR-4) |
| `picocli-spec/DESIGN.md` | EBNF grammar, data flow diagrams, schema reference |
| `picocli-spec-tool/README.md` | picospec CLI usage guide |
| `docs/adr/` | Architecture decision records (custom JSON, dual format, module split) |
| `.github/workflows/ci.yml` | Test matrix (Java 8/11/17/21 × Linux/macOS/Windows) |
| `.github/workflows/release.yml` | Automated release to Maven/Javadoc/gh-pages |

## Common Workflows

### Adding a New Type to ArgTypes

1. Define in `picocli-spec/src/main/java/picocli/spec/ArgTypes.java`:
   ```java
   public static final Map<String, String> BUILTIN_TYPES = Map.of(..., "UUID", "java.util.UUID", ...);
   ```
2. Add converter in `CommandSpec` model if needed
3. Update `command-spec.schema.json` `$defs/argType/enum` to include new type
4. Add roundtrip test in `CommandSpecDslTest` or `CommandSpecJsonTest`
5. Update `CHANGELOG.md` under "Extended `ArgTypes` Vocabulary"

### Implementing a New picospec Subcommand

1. Add new task/handler in `picocli-spec-tool/src/main/java/picocli/spec/tool/SpecToolApp.java`
2. Implement render logic (e.g., extend existing handlers like `PreviewCommand`)
3. Add unit tests in `picocli-spec-tool/src/test/java/picocli/spec/tool/` (e.g., `PreviewTest`)
4. Test end-to-end via `SpecToolAppTest` (runs `CommandLine#execute`)
5. Document in `picocli-spec-tool/README.md`

### Proposing Changes Upstream

1. Check [remkop/picocli#2463](https://github.com/remkop/picocli/pull/2463) and other upstream PRs for status
2. Ensure feature is well-tested and documented in this fork
3. Draft PR against `remkop/picocli:main` with clear motivation
4. Link related ADR or REQUIREMENTS section for context

## Known Constraints & Patterns

- **Multi-Java compilation**: Use `if (JavaVersion.current().isJava9Compatible()) { ... }` in settings.gradle to conditionally include modules
- **Module system (`jvmArgs --add-opens`)**: Required for Java 9+ tests; see test classes in `picocli-tests-java9plus`
- **Windows CI**: Line-ending normalization is critical; `.gitattributes` enforces CRLF handling
- **Shadow plugin (Java 8+)**: Conditionally applied in build.gradle; skipped on Java 6/7
- **Gradle Wrapper**: Pinned version; always use `./gradlew` not `gradle` directly
- **Dependabot**: Auto-merge restricted to `version-update:semver-patch`; manual review for minor/major bumps

## Resources

- **Upstream**: [remkop/picocli](https://github.com/remkop/picocli) — features, user manual, examples
- **Published Artifacts**: [Maven](https://wstein.github.io/picocli/maven) · [Javadoc](https://wstein.github.io/picocli/apidocs/) · [JSON Schema](https://wstein.github.io/picocli/spec/schema/command-spec.schema.json)
- **Repository**: [wstein/cliwerk](https://github.com/wstein/cliwerk) (formerly `wstein/picocli`; GitHub redirects old URLs)
