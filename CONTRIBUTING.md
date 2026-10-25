# Contributing to cliwerk

:+1::star_struck: Wow, thanks for taking the time to contribute! :tada::+1:

The following is a set of guidelines for contributing to [cliwerk](https://github.com/wstein/cliwerk), an independent fork of [picocli](https://github.com/remkop/picocli) (see [docs/FORK.adoc](docs/FORK.adoc)). Contributions to upstream picocli itself go to [remkop/picocli](https://github.com/remkop/picocli). These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

## <a name="questions"></a> Questions and problem reports
GitHub Issues and Discussions are currently not enabled for this repository.
Questions, problem reports and changes all go through [pull requests](https://github.com/wstein/cliwerk/pulls).
Please do not report security vulnerabilities in a pull request; see [SECURITY.md](SECURITY.md) instead.

## <a name="helpful"></a> Helpful problem reports look like this
A helpful problem report saves the maintainer time.
Try to include these elements in the pull request description:

* How to reproduce the problem
* What is the expected behaviour
* What do you actually see

If you can provide code that reproduces the problem, great!
If this code is in the form of a failing unit test, even better!

## <a name="issue_or_pr"></a> Small or large pull requests?
For bugfixes and documentation fixes, a pull request that describes the problem and fixes it is perfectly fine.

For complex changes, it may save time to open a draft pull request early that describes the problem
and the intended shape of the solution, so we can agree on it before the implementation is finished.


## <a name="pr_conventions"></a> Conventions for pull requests
Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`, `chore:`, ...) for commit messages, as in the existing history.


## <a name="java_version"></a> Java version
The project is built with Java 8, and different artifacts target different versions of Java.
See the table below.

Artifact | Target Java Version
------------ | -------------
`picocli` | Java 5
`picocli-codegen` | Java 6
`picocli-groovy` | Java 5
`picocli-shell-jline2` | Java 5
`picocli-shell-jline3` | Java 8
`picocli-spring-boot-starter` | Java 8

Please be aware that pull requests can only use language features that are supported in the above version of Java.

## <a name="build"> Building

```
git clone https://github.com/wstein/cliwerk.git
cd cliwerk
gradlew publishToMavenLocal
```

That should publish the current version (`projectVersion` in `dependencies.gradle`) to your local .m2 Maven cache under cliwerk's coordinates `io.github.wstein:cliwerk` (see [docs/FORK.adoc](docs/FORK.adoc)). You can then try this in a project that uses that dependency.
