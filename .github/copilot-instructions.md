# GitHub Copilot Instructions

This repository uses a unified agent guidance file for all AI systems.

**See `/agents.md` for complete development guidance.**

## Quick Reference

### Build & Test
- Build: `./gradlew clean build`
- Tests: `./gradlew test`
- Single test: `./gradlew :module:test --tests ClassName`
- Gradle wrapper: always use `./gradlew`, never `gradle`

### Key Modules
- **picocli** (root module, published as cliwerk): Core annotation/programmatic API
- **picocli-spec**: DSL/JSON parser for CommandSpec (Java 11 target)
- **picocli-spec-tool**: Standalone CLI tool (picospec) for spec operations
- **picocli-codegen**: Man page and completion generation

### Architecture Principles
1. **Zero Runtime Dependencies**: No Jackson, Gson, or external JSON libs (embedded parser in picocli-spec)
2. **Dual Format**: Both `.picocli` (DSL) and `.json` supported with lossless round-trip conversion
3. **Spec Composition**: External specs can be merged as subcommands via `CommandSpecMerger`
4. **Multi-Java**: Tests split by Java version (5/6/7, 8, 9+); check `settings.gradle` for conditionally-included modules

### Testing Strategy
- Round-trip fidelity tests verify DSL ↔ JSON conversion
- Tests organized by Java compatibility in separate modules
- Fixtures in `src/test/resources/picocli/spec/examples/`

### Release
- Version in `dependencies.gradle`
- Tag: `v<version>` triggers automated release.yml
- Publishes to Maven (gh-pages), Javadoc, schema to gh-pages

### Before You Code
- **If adding types**: Update `ArgTypes`, `command-spec.schema.json`, and add tests
- **If new picospec subcommand**: Implement handler, add tests, document in README
- **If Java version-specific**: Ensure conditionally-included in `settings.gradle`

## Refer to `/agents.md`

For detailed guidance on architecture decisions (ADRs), common workflows, known constraints, and resources.
