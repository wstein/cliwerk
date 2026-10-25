# ADR-001: Custom Minimal JSON Parser for cliwerk-spec

## Status
Accepted

## Context
The `cliwerk-spec` module enables declaring picocli `CommandSpec` models via JSON (conforming to `command-spec.schema.json`).

Standard Java JSON libraries such as Jackson, Gson, or Fastjson would introduce third-party transitive dependencies. However, picocli core has an uncompromising design principle of zero external runtime dependencies. Introducing Jackson or Gson would add megabytes of transitive dependencies, potential CVE exposure, and classpath collision risks for host applications embedding picocli.

## Decision
Implement a lightweight, self-contained JSON parser (`cliwerk.spec.json.Json`) within `cliwerk-spec` with zero external dependencies.

The parser:
- Parses JSON text into standard Java runtime collections (`Map<String, Object>`, `List<Object>`, `String`, `Boolean`, `Number`, `null`).
- Uses pure Java Standard Library data structures (`LinkedHashMap`, `ArrayList`, `StringBuilder`).
- Only implements parsing (reading), while `CommandSpecJson.write()` emits clean formatted JSON directly via `StringBuilder`.

## Consequences

### Positive
- Zero runtime dependencies beyond `picocli` core.
- Minimal footprint: only ~150 lines of code.
- No version conflicts or classpath pollution for downstream consumers.
- Native performance with minimal allocation overhead for CLI spec sizes.

### Negative
- Does not support arbitrary JSON features or exotic encodings beyond standard UTF-8 JSON.
- Not intended as a general-purpose JSON library for users.
