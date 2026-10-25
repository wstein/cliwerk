# ADR-002: Dual DSL and JSON Format Strategy

## Status
Accepted

## Context
Command-line specifications need to serve two distinct personas and workflows:
1. Human authors writing and maintaining CLI definitions by hand without complex tooling.
2. Machine tooling, language servers, schema validators, code generators, and IDE integrations.

A JSON-only format is verbose and error-prone to write by hand (lack of trailing commas, noisy quote requirements, no comments in standard JSON). Conversely, a DSL-only format is difficult for third-party tools to inspect or generate without implementing a custom grammar parser.

## Decision
Support dual complementary formats with lossless bidirectional conversion:
- **DSL (`.cliwerk`)**: A lightweight, human-friendly text format supporting line comments (`//`), concise option/positional definitions, definition blocks, bundles, and readable command hierarchies.
- **JSON (`command-spec.schema.json`)**: A machine-readable, schema-validated JSON format for editor autocomplete, CI validation, and external code generation.
- **Round-trip fidelity**: Both formats parse into the identical `CommandSpec` internal model, and `cliwerk-spec convert` allows lossless round-tripping between DSL and JSON.

## Consequences

### Positive
- Developers get an ergonomic, readable text syntax for hand authoring CLI specs.
- Tooling, editors, and external systems get standard JSON Schema validation and autocomplete.
- Seamless conversion via `cliwerk-spec convert --to-json` and `cliwerk-spec convert --to-dsl`.

### Negative
- Requires maintaining both a DSL lexer/parser (`CommandSpecDsl`) and a JSON reader/writer (`CommandSpecJson`).
- Grammatical additions must be reflected and tested across both representations.
