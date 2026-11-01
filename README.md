# cliwerk

[![CI](https://github.com/wstein/cliwerk/actions/workflows/ci.yml/badge.svg)](https://github.com/wstein/cliwerk/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/release/wstein/cliwerk.svg)](https://github.com/wstein/cliwerk/releases)
[![License](https://img.shields.io/github/license/wstein/cliwerk.svg)](LICENSE)

> **cliwerk is not affiliated with or endorsed by the picocli project.**
> "picocli" refers to the upstream project. cliwerk releases are not picocli releases.
> cliwerk is an independent fork of [picocli](https://github.com/remkop/picocli) by Remko Popma,
> maintained by [wstein](https://github.com/wstein) under the [Apache License 2.0](LICENSE) (see [NOTICE](NOTICE)).

## What is cliwerk?

cliwerk is a Java library for building command line applications with options, subcommands,
usage help and shell completion. It is a fork of picocli and a drop-in replacement for it:
the Java package is still `picocli`, so your imports and annotations stay the same.
Only the Maven coordinates differ: `io.github.wstein:cliwerk` instead of `info.picocli:picocli`.

Why use it? You get the picocli API plus fish and PowerShell completion,
command specs built from a DSL or JSON, and on-demand help sections.
See [Features](docs/FEATURES.adoc).

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

More options (plain jars, requirements): [docs/INSTALL.adoc](docs/INSTALL.adoc).

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

## Documentation

| Topic | Where |
| --- | --- |
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
