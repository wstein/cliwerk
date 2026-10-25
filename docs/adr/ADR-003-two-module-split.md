# ADR-003: Two-Module Split (cliwerk-spec vs cliwerk-spec-tool)

## Status
Accepted

## Context
The `cliwerk-spec` feature family provides two main capabilities:
1. Core parsing, model generation, and composition (`CommandSpecDsl`, `CommandSpecJson`, `CommandSpecMerger`, `SpecValidator`).
2. CLI developer tooling: previewing CLI usage help, generating shell completions, generating AsciiDoc man pages, and converting formats.

Generating AsciiDoc man pages depends on `picocli-codegen` (`ManPageGenerator`). If `cliwerk-spec` bundled man page generation directly, all applications embedding `cliwerk-spec` at runtime would transitively depend on `picocli-codegen` and its build requirements.

## Decision
Split functionality into two separate Gradle sub-modules:
- **`cliwerk-spec`**: Zero-dependency runtime library (depends only on `picocli` core). Contains DSL/JSON parsing and serialization, `ArgTypes`, merger, and validator.
- **`cliwerk-spec-tool`**: Standalone CLI utility (`cliwerk-spec`). Depends on `cliwerk-spec`, `picocli-codegen`, and `picocli`. Published as a shadow fat-jar (`cliwerk-spec-tool-<version>-all.jar`) containing all dependencies for direct command-line execution (`cliwerk-spec preview|completion|manpage|validate|convert`).

## Consequences

### Positive
- Strict isolation of dependencies: applications embedding spec models remain lightweight and zero-dependency.
- Build tooling (man page generation, shadow jar packaging) remains isolated to the developer tool.
- Clear separation between the specification engine and operator commands.

### Negative
- Users needing man page generation in their build pipelines must invoke the CLI tool or depend on `cliwerk-spec-tool` rather than `cliwerk-spec` alone.
