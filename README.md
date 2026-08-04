# Methodologist Setup Service

A Spring Boot REST API service for the Vitruv methodologist workflow. It validates and standardizes
GenModel files for MWE2 compatibility, and generates ready-to-build VSUM projects from uploaded
metamodel, genmodel, and reaction files.

## Features

- **GenModel Validation**: Inspect GenModel files for compliance issues without modifying them
- **GenModel Processing**: Apply standardization rules and download the corrected file
- **VSUM Project Generation**: Build a complete VSUM project from metamodels, genmodels, and
  reactions, returned as a zip archive
- **VSUM Jar Build**: Build the project and return only the executable jar-with-dependencies
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
java -jar target/methodologist-setup-service-0.0.1-SNAPSHOT.jar
```

The service starts on `http://localhost:8090`.

### Run with Docker

The `Dockerfile` copies the jar from the build context root, and `target/` is excluded via
`.dockerignore`, so the jar must be staged first:

```bash
./mvnw clean package
cp target/methodologist-setup-service-0.0.1-SNAPSHOT.jar .
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

### Build VSUM Project

**POST** `/api/vsum/build` (`multipart/form-data`)

Uploads metamodel, genmodel, and reaction files, generates the project from FreeMarker templates,
builds it, and returns the whole project as a zip archive (`vsum-project-<timestamp>.zip`).

Parts:

| Part | Required | Description |
| --- | --- | --- |
| `metamodelFiles` | yes | One or more `.ecore` metamodel files |
| `genmodelFiles` | yes | `.genmodel` files, paired **by index** with `metamodelFiles` |
| `reactionFiles` | yes | One or more `.reactions` files |

`metamodelFiles` and `genmodelFiles` must have the same count — index *n* of one is matched with
index *n* of the other. Reaction imports are normalized against the uploaded metamodels' nsURIs.

```bash
curl -X POST \
  -F "metamodelFiles=@model.ecore" \
  -F "genmodelFiles=@model.genmodel" \
  -F "reactionFiles=@example.reactions" \
  -o vsum-project.zip \
  http://localhost:8090/api/vsum/build
```

### Build VSUM Jar

**POST** `/api/vsum/jar` (`multipart/form-data`)

Same inputs and pairing rules as `/api/vsum/build`, but returns only the executable
jar-with-dependencies produced under `vsum/target` (`application/java-archive`).

```bash
curl -X POST \
  -F "metamodelFiles=@model.ecore" \
  -F "genmodelFiles=@model.genmodel" \
  -F "reactionFiles=@example.reactions" \
  -o vsum.jar \
  http://localhost:8090/api/vsum/jar
```

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
│   └── service/              # GenModel precheck and file services
├── vsum/
│   ├── controller/           # VSUM REST controller
│   └── service/              # Project generation, templating, and build
├── config/                   # Swagger, Vitruv config, exception handling, class loading
├── emf/                      # EMF model initialization
├── exception/                # Custom exceptions and error DTOs
├── log/                      # Request/response logging filter
├── messages/                 # Error and info message constants
├── util/                     # File helpers
├── ResponseTemplateDto.java  # Generic API response envelope
└── MethodologistSetupServiceApplication.java

src/main/resources/templates/ # FreeMarker templates for generated VSUM projects
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
- `500 Internal Server Error`: Unexpected server error

Errors are handled centrally in `GlobalExceptionHandler` and returned as structured responses with
a message and error code to help with debugging.