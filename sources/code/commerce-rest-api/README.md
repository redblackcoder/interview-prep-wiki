# commerce

Spring Boot REST service (API-first with OpenAPI codegen).

- Java 17, Spring Boot 3.4.1, Maven.
- Resources: item (each with full CRUD).
- API base path: `/api` (e.g. `http://localhost:9765/api/items`).
- Swagger UI: http://localhost:9765/api/swagger-ui.html
- OpenAPI JSON: http://localhost:9765/api/v3/api-docs

## Run the backend

```sh
mvn spring-boot:run
```

The OpenAPI spec lives at `src/main/resources/openapi/openapi.yaml`. Editing it
regenerates the API interfaces + models on the next `mvn` build; controllers in
`src/main/java/com/practice/commerce/controller` implement those interfaces.

## Test

JUnit 5 + AssertJ + Mockito (via `spring-boot-starter-test`). One test per
resource lives in `src/test/java/com/practice/commerce/controller`.

```sh
mvn test
```

## Try it

```sh
curl -X POST http://localhost:9765/api/items \
  -H 'Content-Type: application/json' -d '{"name":"example"}'
curl http://localhost:9765/api/items
```

## Dependency resolution

This project resolves artifacts directly from **Maven Central** via a local
`settings.xml` (applied automatically by `.mvn/maven.config`), bypassing any
corporate Nexus proxy in your global `~/.m2/settings.xml`. `.vscode/settings.json`
points VS Code / Cursor's Java extension at the same file. To use your global
settings instead (e.g. for internal artifacts), delete `settings.xml`,
`.mvn/maven.config` and the `java.configuration.maven.userSettings` entry.
