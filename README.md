# Methodologist Setup Service

A Spring Boot REST API service for validating and standardizing GenModel files for MWE2 workflow compatibility.

## Features

- **GenModel Validation**: Inspect GenModel files for compliance issues
- **GenModel Processing**: Automatically apply standardization rules to GenModel files
- **REST API**: Clean and intuitive RESTful endpoints
- **Error Handling**: Comprehensive error reporting and validation
- **Docker Support**: Ready-to-use Docker setup

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker (optional)

### Build

```bash
mvn clean package
```

### Run Locally

```bash
java -jar target/methodologist-setup-service-0.0.1-SNAPSHOT.jar
```

The service will start on `http://localhost:8090`

### Run with Docker

```bash
docker compose up --build
```

## API Endpoints

### Inspect GenModel

**POST** `/api/genmodel/inspect`

Analyzes a GenModel file and **previews** what changes would be applied.

```bash
curl -X POST -F "file=@mymodel.genmodel" http://localhost:8090/api/genmodel/inspect
```

Response:

```json
{
  "status": "success",
  "message": "GenModel inspected successfully, showing planned changes",
  "issues": [
    {
      "filename": "mymodel.genmodel",
      "message": "Would remove attributes: complianceLevel"
    }
  ]
}
```

### Process GenModel

**POST** `/api/genmodel/process`

Analyzes a GenModel file and **applies** the standardization changes.

```bash
curl -X POST -F "file=@mymodel.genmodel" http://localhost:8090/api/genmodel/process
```

Response:

```json
{
  "status": "success",
  "message": "GenModel processed and changes applied successfully",
  "issues": [
    {
      "filename": "mymodel.genmodel",
      "message": "Removed attributes: complianceLevel"
    }
  ]
}
```

## Standardization Rules

The service automatically applies these normalization rules:

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
├── api/
│   ├── controller/        # REST controllers
│   └── dto/               # Data Transfer Objects
├── service/               # Business logic layer
└── MethodologistSetupServiceApplication.java
```

## Health Check

```bash
curl http://localhost:8090/actuator/health
```

## Configuration

See `src/main/resources/application.properties` for available configurations.

Default settings:

- Port: `8090`
- Application Name: `methodologist-setup-service`

## Technologies

- **Framework**: Spring Boot 4.0.6
- **Modeling**: Eclipse EMF (EMF Ecore, EMF GenModel, EMF Codegen)
- **XML Processing**: StAX (Streaming API for XML)
- **Build Tool**: Maven 3.8+
- **Java Version**: 21 LTS

## Documentation

For detailed API documentation, see [API_DOCUMENTATION.md](API_DOCUMENTATION.md)

## Error Handling

All requests return appropriate HTTP status codes:

- `200 OK`: Successful operation
- `400 Bad Request`: Invalid input or processing error
- `500 Internal Server Error`: Unexpected server error

Error responses include detailed messages to help with debugging.
