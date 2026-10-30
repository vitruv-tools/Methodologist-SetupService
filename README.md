# Methodologist Setup Service

A Spring Boot REST API service for the Vitruv methodologist workflow. It validates and standardizes
GenModel files for MWE2 compatibility, and builds VSUMs from uploaded metamodels, reactions,
constraints, and view definitions.

## Features

- **GenModel Validation**: Inspect GenModel files for compliance issues without modifying them
- **GenModel Processing**: Apply standardization rules and download the corrected file
- **VSUM Build**: Generate genmodels and a Methodologist-Template project from metamodels, reactions,
  and VitruvOCL constraints, build and smoke-test it, and return the project zip or executable jar
- **OpenAPI / Swagger UI**: Interactive API documentation
- **Error Handling**: Centralized exception handling with structured error responses
- **Docker Support**: Dockerfile and Compose setup included

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+ (or the bundled `./mvnw` wrapper)
- Docker (optional)

### Build

```bash
./mvnw clean package
```

### Run Locally

```bash
java -jar target/methodologist-setup-service-1.0.1.jar
```

The service starts on `http://localhost:8090`.

### Run with Docker

```bash
docker compose up --build
```

The image builds the service itself and then builds a sample VSUM project once, which fills the image's
Maven repository (`/opt/vsum-m2`) with every dependency of the generated projects. In the container, VSUM
builds run offline: they do not depend on Maven Central, and the first build after a deployment is as fast
as the others. Building the image needs network access.

To change the Vitruvius, VitruvOCL, EMF, or plugin versions, change them in `VsumBuildProperties` and
rebuild the image. A version overridden at runtime (`SETUP_VSUM_*_VERSION`) that is missing from the image
is downloaded once and logged (`setup.vsum.online-fallback`); set `SETUP_VSUM_ONLINE_FALLBACK=false` to
fail instead.

On startup, the container builds the sample project again, offline. Until that succeeds,
`/actuator/health/readiness` reports `OUT_OF_SERVICE`, so a deployment with a broken repository or
incompatible versions never receives traffic.

## API Endpoints

Interactive docs: `http://localhost:8090/swagger-ui.html` — OpenAPI spec: `http://localhost:8090/api-docs`

### Inspect GenModel

**POST** `/api/genmodel/inspect` (`multipart/form-data`)

Analyzes a GenModel file and **previews** what changes would be applied. Returns JSON.

```bash
curl -X POST -F "file=@mymodel.genmodel" http://localhost:8090/api/genmodel/inspect
```

Response:

```json
{
  "data": [
    {
      "filename": "mymodel.genmodel",
      "message": "Would remove attributes: complianceLevel"
    }
  ],
  "message": "GenModel inspected successfully, showing planned changes"
}
```

### Process GenModel

**POST** `/api/genmodel/process` (`multipart/form-data`)

Applies the standardization rules and returns the **processed file itself** as a download
(`application/octet-stream`), not a JSON summary.

```bash
curl -X POST -F "file=@mymodel.genmodel" \
  -o mymodel.processed.genmodel \
  http://localhost:8090/api/genmodel/process
```

### Build VSUM

**POST** `/api/vsum/build` returns the generated project as a zip; **POST** `/api/vsum/jar` returns the
executable VSUM jar, which hosts the VSUM in a Vitruvius server (`java -jar <jar> [port]`, type `quit` to stop).

| Part              | Required | Content                                                   |
|-------------------|----------|-----------------------------------------------------------|
| `metamodelFiles`  | yes      | Ecore metamodels (`.ecore`)                               |
| `reactionFiles`   | no       | Reactions consistency specifications (`.reactions`)       |
| `constraintFiles` | no       | VitruvOCL constraints (`.ocl`)                            |
| `viewFiles`       | no       | NeoJoin view definitions (stored in the project, not compiled yet) |

```bash
curl -X POST \
  -F metamodelFiles=@model.ecore -F metamodelFiles=@model2.ecore \
  -F reactionFiles=@templateReactions.reactions \
  -F constraintFiles=@constraints.ocl \
  -o vsum.jar http://localhost:8090/api/vsum/jar
```

Genmodels are not uploaded; they are generated from the metamodels. The pipeline:

1. Validates the uploads (non-empty, expected extension, unique names).
2. Loads the metamodels, resolves references between them (relative, `platform:/resource`, or nsURI),
   validates them with EMF, and generates matching genmodels.
3. Checks that every Reactions `import` refers to an uploaded nsURI.
4. Generates a project following the [Methodologist Template](https://github.com/vitruv-tools/Methodologist-Template)
   (`model`, `consistency`, `vsum`), with a smoke test that creates and reloads the VSUM and compiles the constraints.
5. Runs `mvn clean verify`.

Invalid inputs are rejected with `400` before Maven runs (`VSUM_INPUT_ERROR`, `VSUM_METAMODEL_ERROR`,
`VSUM_REACTIONS_ERROR`). Failed Maven builds are classified by their output, so that only problems of the
uploads are reported as such:

| Status | Error code               | Cause                                                                                     |
|--------|--------------------------|-------------------------------------------------------------------------------------------|
| `422`  | `VSUM_BUILD_FAILED`      | The uploaded metamodels, reactions, or constraints do not compile, or the smoke test fails |
| `503`  | `VSUM_BUILD_UNAVAILABLE` | The build environment failed (dependency download, disk space, memory); retried once first |
| `500`  | `VSUM_INTERNAL_ERROR`    | Code the service generates itself does not build, or the library versions are incompatible |
| `504`  | `VSUM_BUILD_TIMEOUT`     | The build exceeded `setup.vsum.build-timeout`; the message names the module being built   |

Errors of the uploads are attributed to the file that causes them, with the line of the upload,
an excerpt, and a hint where one is known. They are listed in `problems`, root causes first; `message`
contains the same as readable text:

```json
{
  "errorCode": "VSUM_BUILD_FAILED",
  "status": 422,
  "message": "The VSUM build failed:\n\nmy.reactions:\n- line 51, column 30: 'Entitiy' cannot be resolved\n...",
  "problems": [
    {
      "source": "REACTIONS",
      "file": "my.reactions",
      "line": 51,
      "column": 30,
      "message": "'Entitiy' cannot be resolved",
      "excerpt": "    val entity = new model2::Entitiy\n                             ^"
    }
  ]
}
```

`source` is `METAMODEL`, `REACTIONS`, `CONSTRAINTS`, `VIEWS`, `INFRASTRUCTURE`, or `INTERNAL`; `file`,
`line`, `column`, `excerpt`, `hint`, and `hiddenFollowUpErrors` are omitted when unknown. Errors that only
follow from another one, e.g. everything after a syntax error in the same reactions file, are not listed
but counted in `hiddenFollowUpErrors`. Problems are reported for metamodels and reactions rejected before
the build, for Reactions compiler and Java compiler errors, and for failures of the smoke test (constraints
that cannot be evaluated, exceptions in reactions or routines while creating the VSUM).

The workspaces of `503` and `500` builds are kept in the temporary directory for debugging and deleted on a
later startup once older than `setup.vsum.workspace-retention`.

Build settings are configured with `setup.vsum.*` properties (see `VsumBuildProperties`), e.g.
`setup.vsum.build-timeout`, `setup.vsum.max-concurrent-builds`, `setup.vsum.maven-arguments`,
`setup.vsum.maven-repository`, `setup.vsum.offline`, `setup.vsum.maven-opts` (the builds' JVM options;
the service's `JAVA_TOOL_OPTIONS` are not passed on), the Vitruvius/VitruvOCL versions, and
`setup.vsum.keep-failed-workspaces` for debugging. Concurrent builds share the local repository with
file locking (Maven 3.9+).

## Standardization Rules

The GenModel endpoints apply these normalization rules:

1. **Removes deprecated attributes:**
    - `complianceLevel`, `compliance`, `editDirectory`, `editorDirectory`, etc.

2. **Ensures correct basePackage:**
    - Sets basePackage to match modelPluginID for all GenPackages

3. **Standardizes modelDirectory:**
    - Format: `/{modelPluginID}/target/generated-sources/ecore`

4. **Enforces creationIcons:**
    - Sets to `false`

5. **Ensures foreignModel reference:**
    - Adds missing foreignModel entry automatically

## Project Structure

```
src/main/java/tools/vitruv/methodologist/setup/
├── model/
│   ├── controller/           # GenModel REST controller + DTOs
│   └── service/              # GenModel precheck, file, and ecore-to-genmodel services
├── vsum/
│   ├── controller/           # VSUM REST controller
│   └── service/              # VSUM build pipeline (metamodel, reactions, constraints, views,
│                             # project generation, Maven build)
├── config/                   # Swagger, exception handling
├── exception/                # Custom exceptions and error DTOs
├── log/                      # Request/response logging filter
├── messages/                 # Error and info message constants
├── ResponseTemplateDto.java  # Generic API response envelope
└── MethodologistSetupServiceApplication.java

src/main/resources/templates/vsum/ # FreeMarker templates for generated VSUM projects
```

## Health Check

```bash
curl http://localhost:8090/actuator/health
curl http://localhost:8090/actuator/health/liveness
curl http://localhost:8090/actuator/health/readiness   # includes the VSUM build warm-up
```

## Configuration

See `src/main/resources/application.properties`.

Default settings:

- Port: `8090`
- Application name: `methodologist-setup-service`
- Swagger UI: `/swagger-ui.html`
- OpenAPI docs: `/api-docs`
- Log level for `tools.vitruv.methodologist`: `DEBUG`

## Technologies

- **Framework**: Spring Boot 4.0.6
- **Modeling**: Eclipse EMF (Ecore 2.43.0, XMI 2.41.0, Codegen 2.47.0)
- **Templating**: FreeMarker
- **API Docs**: springdoc-openapi 3.0.0
- **Logging**: Logback with logstash JSON encoder
- **Build Tool**: Maven (Spotless, Checkstyle, Surefire)
- **Java Version**: 21 LTS

## Development

```bash
./mvnw clean verify        # full build with tests
./mvnw verify -Dvsum.integration=true   # also builds generated VSUM projects with Maven
./mvnw spotless:apply      # auto-format sources
./mvnw checkstyle:check    # style check against checkstyle.xml
```

CI (`.github/workflows/ci.yml`) runs `mvnw clean verify` on PRs to `main` and `develop`, builds
generated VSUM projects with Maven, builds the Docker image and checks that it builds VSUMs offline,
plus nightly SonarQube analysis and a Checkstyle review pass.

## Error Handling

All requests return appropriate HTTP status codes:

- `200 OK`: Successful operation
- `400 Bad Request`: Invalid input or processing error
- `413 Content Too Large`: Uploads exceed the configured size limit
- `422 Unprocessable Content`: Invalid GenModel, or the generated VSUM does not build
- `500 Internal Server Error`: Unexpected server error, or the generated VSUM project fails for reasons
  of the service itself (`VSUM_INTERNAL_ERROR`)
- `503 Service Unavailable`: The VSUM build environment failed; retrying later may succeed
- `504 Gateway Timeout`: The VSUM build exceeded its timeout

Errors are handled centrally in `GlobalExceptionHandler` and returned as structured responses with
a message and error code to help with debugging.