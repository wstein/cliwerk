# cliwerk

[![CI](https://github.com/wstein/cliwerk/actions/workflows/ci.yml/badge.svg)](https://github.com/wstein/cliwerk/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/release/wstein/cliwerk.svg)](https://github.com/wstein/cliwerk/releases)
[![License](https://img.shields.io/github/license/wstein/cliwerk.svg)](LICENSE)

[Website](https://wstein.github.io/cliwerk/) ·
[Install](#install) ·
[Quickstart](#hello-cliwerk) ·
[Docs](#documentation) ·
[Javadoc](https://wstein.github.io/cliwerk/apidocs/cliwerk/) ·
[Migration](docs/MIGRATION.adoc) ·
[FAQ](docs/FAQ.adoc) ·
[Changelog](CHANGELOG.md)

> **cliwerk is not affiliated with or endorsed by the picocli project.**
> "picocli" refers to the upstream project. cliwerk releases are not picocli releases.
> cliwerk is an independent fork of [picocli](https://github.com/remkop/picocli) by Remko Popma,
> maintained by [wstein](https://github.com/wstein) under the [Apache License 2.0](LICENSE) (see [NOTICE](NOTICE)).

## What is cliwerk?

cliwerk is a Java library for building command line applications with options, subcommands,
usage help and shell completion.

## Why use it?

cliwerk contains the whole picocli API. If you build on picocli, you also get:

| What cliwerk adds | Details |
| --- | --- |
| fish completion | `AutoComplete.fish()`, [Features](docs/FEATURES.adoc#_fish_and_powershell_completion) |
| PowerShell completion | `AutoComplete.powershell()`, [Features](docs/FEATURES.adoc#_fish_and_powershell_completion) |
| Command specs from a DSL or JSON | `cliwerk-spec`, no annotated classes, [Features](docs/FEATURES.adoc#_cliwerk_spec) |
| On-demand help sections | `helpSection` on `@Option`, `@Parameters`, `@ArgGroup`, `@Command`, [Features](docs/FEATURES.adoc#_on_demand_help_sections) |

## Switching from picocli in 30 seconds

> Change only the coordinates: `info.picocli:picocli` becomes `io.github.wstein:cliwerk`.
> The Java package stays `picocli`, so your imports and annotations do not change.
> Add the cliwerk repository (see [Install](#install)) and build.
> Details and edge cases: [Migration](docs/MIGRATION.adoc).

## Install

Current version: `4.7.7.1` (based on upstream picocli 4.7.7).

```groovy
// Gradle
repositories {
    maven { url 'https://wstein.github.io/cliwerk/maven' }
}
dependencies {
    implementation 'io.github.wstein:cliwerk:4.7.7.1'
}
```

```xml
<!-- Maven -->
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

cliwerk is published to a Maven repository on GitHub Pages and as jars on
[GitHub Releases](https://github.com/wstein/cliwerk/releases), not to Maven Central.
The core classes are Java 8 bytecode; the `cliwerk-spec` classes need Java 11 or later.
More options and requirements: [docs/INSTALL.adoc](docs/INSTALL.adoc).

## Hello cliwerk

```java
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "hello", mixinStandardHelpOptions = true, version = "hello 1.0",
         description = "Prints a greeting.")
public class Hello implements Callable<Integer> {

    @Option(names = {"-n", "--name"}, description = "Who to greet (default: ${DEFAULT-VALUE}).")
    private String name = "world";

    @Option(names = {"-c", "--count"}, description = "How many times to greet.")
    private int count = 1;

    @Override
    public Integer call() {
        for (int i = 0; i < count; i++) {
            System.out.println("Hello, " + name + "!");
        }
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Hello()).execute(args));
    }
}
```

Save this as `Hello.java` and run it with the Java 11+ source launcher, with the cliwerk jar on the class path:

```
$ java -cp cliwerk-4.7.7.1.jar Hello.java --name cliwerk --count 2
Hello, cliwerk!
Hello, cliwerk!

$ java -cp cliwerk-4.7.7.1.jar Hello.java --help
Usage: hello [-hV] [-c=<count>] [-n=<name>]
Prints a greeting.
  -c, --count=<count>   How many times to greet.
  -h, --help            Show this help message and exit.
  -n, --name=<name>     Who to greet (default: world).
  -V, --version         Print version information and exit.
```

## Next steps

- [Features](docs/FEATURES.adoc): what cliwerk adds on top of picocli
- [Migration](docs/MIGRATION.adoc): switch an existing picocli project
- [FAQ](docs/FAQ.adoc)
- [Javadoc](https://wstein.github.io/cliwerk/apidocs/cliwerk/) and
  [cliwerk-spec Javadoc](https://wstein.github.io/cliwerk/apidocs/cliwerk-spec/)
- [JSON Schema](https://wstein.github.io/cliwerk/spec/schema/command-spec.schema.json) for spec files

## Documentation

| Topic | Where |
| --- | --- |
| Website | [wstein.github.io/cliwerk](https://wstein.github.io/cliwerk/) |
| Install | [docs/INSTALL.adoc](docs/INSTALL.adoc) |
| Quickstart | [Hello cliwerk](#hello-cliwerk) (above) |
| Features | [docs/FEATURES.adoc](docs/FEATURES.adoc) |
| Migration from picocli | [docs/MIGRATION.adoc](docs/MIGRATION.adoc) |
| FAQ | [docs/FAQ.adoc](docs/FAQ.adoc) |
| Javadoc | [cliwerk API](https://wstein.github.io/cliwerk/apidocs/cliwerk/) |
| cliwerk-spec Javadoc | [cliwerk-spec API](https://wstein.github.io/cliwerk/apidocs/cliwerk-spec/) |
| JSON Schema | [command-spec.schema.json](https://wstein.github.io/cliwerk/spec/schema/command-spec.schema.json) |
| Upstream manual | [picocli.info](https://picocli.info) (applies to everything cliwerk has not changed) |
| Fork rules and decisions | [docs/FORK.adoc](docs/FORK.adoc) |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |

## Project

- License: [Apache License 2.0](LICENSE), see [NOTICE](NOTICE).
- Upstream: [remkop/picocli](https://github.com/remkop/picocli). cliwerk keeps the picocli API; the API only grows.
- Version: `4.7.7.1`, based on upstream picocli 4.7.7.

## Status and contact

cliwerk is an independently maintained personal fork. It is maintained on a best-effort basis,
with no support guarantee.

Bug reports and questions: [GitHub Issues](https://github.com/wstein/cliwerk/issues).
Changes and fixes: [pull requests](https://github.com/wstein/cliwerk/pulls), see [CONTRIBUTING.md](CONTRIBUTING.md).
Security reports follow [SECURITY.md](SECURITY.md), not public issues.
