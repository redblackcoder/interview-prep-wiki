# lsm

Plain Java 17 Maven project with JUnit 5 + AssertJ.

## Run

```sh
mvn -q compile exec:java
mvn -q compile exec:java -Dexec.args="Ada"
```

Or build a JAR:

```sh
mvn package
java -jar target/lsm-0.0.1-SNAPSHOT.jar Ada
```

## Test

Tests live in `src/test/java/com/practice/lsm/` (see `MainTest.java`).

```sh
mvn test                              # all tests
mvn test -Dtest=MainTest              # one class
mvn test -Dtest='MainTest#greet*'     # matching methods
```

## Dependency resolution

This project resolves artifacts directly from **Maven Central** via a local
`settings.xml` (applied automatically by `.mvn/maven.config`), bypassing any
corporate Nexus proxy in your global `~/.m2/settings.xml`. `.vscode/settings.json`
points VS Code / Cursor's Java extension at the same file. To use your global
settings instead, delete `settings.xml`, `.mvn/maven.config` and the
`java.configuration.maven.userSettings` entry.
