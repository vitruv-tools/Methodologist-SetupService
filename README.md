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

The `Dockerfile` copies the jar from the build context root, and `target/` is excluded via
`.dockerignore`, so the jar must be staged first:

```bash
./mvnw clean package
cp target/methodologist-setup-service-1.0.1.jar .
docker compose up --build
```

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

**POST** `/api/vsum/project` returns the generated project as a zip, laid out like the Methodologist
Template and not built; build it yourself with `mvn clean verify`. **POST** `/api/vsum/jar` builds the
project with Maven and returns the executable VSUM jar, which hosts the VSUM in a Vitruvius server
(`java -jar <jar> [port]`, type `quit` to stop).

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
`VSUM_REACTIONS_ERROR`). If the generated VSUM does not build, the response is `422` (`VSUM_BUILD_FAILED`) with
the relevant Maven errors; a timeout is reported as `504` (`VSUM_BUILD_TIMEOUT`).

Build settings are configured with `setup.vsum.*` properties (see `VsumBuildProperties`), e.g.
`setup.vsum.build-timeout`, `setup.vsum.max-concurrent-builds`, `setup.vsum.maven-arguments`,
the Vitruvius/VitruvOCL versions, and `setup.vsum.keep-failed-workspaces` for debugging.

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
- **Modeling**: Eclipse EMF 2.35.0 (Ecore, XMI, Codegen)
- **Templating**: FreeMarker
- **API Docs**: springdoc-openapi 3.0.0
- **Logging**: Logback with logstash JSON encoder
- **Build Tool**: Maven (Spotless, Checkstyle, Surefire)
- **Java Version**: 21 LTS

## Development

```bash
./mvnw clean verify        # full build with tests
./mvnw spotless:apply      # auto-format sources
./mvnw checkstyle:check    # style check against checkstyle.xml
```

CI (`.github/workflows/ci.yml`) runs `mvnw clean verify` on PRs to `main` and `develop`, plus
nightly SonarQube analysis and a Checkstyle review pass.

## Error Handling

All requests return appropriate HTTP status codes:

- `200 OK`: Successful operation
- `400 Bad Request`: Invalid input or processing error
- `413 Content Too Large`: Uploads exceed the configured size limit
- `422 Unprocessable Content`: Invalid GenModel, or the generated VSUM does not build
- `500 Internal Server Error`: Unexpected server error
- `504 Gateway Timeout`: The VSUM build exceeded its timeout

Errors are handled centrally in `GlobalExceptionHandler` and returned as structured responses with
a message and error code to help with debugging.