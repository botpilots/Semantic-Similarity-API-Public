# Semantic Similarity API

A Quarkus-based REST API for processing XML documents and identifying groups of similar sentences using vector embeddings and cosine similarity.

## Features

- **XML Processing**: Extract sentences from XML documents
- **XPath Support**: Target specific elements in XML documents using XPath expressions
- **Sentence Vectorization**: Convert sentences to vector embeddings
- **Similarity Analysis**: Group similar sentences based on cosine similarity
- **Asynchronous Processing**: Process documents asynchronously with session-based result retrieval

## Getting Started

### Prerequisites

- Java 11 or higher
- Maven 3.8.1 or higher

### Running the Application

1. Clone the repository
2. Navigate to the project directory
3. Run the application in development mode:

```bash
./mvnw compile quarkus:dev
```

The application will be available at http://localhost:8080

### Building the Application

To build the application:

```bash
./mvnw package
```

This will produce a `semsim-1.0.0-SNAPSHOT-runner.jar` file in the `/target` directory.

### Running Tests

To run the tests:

```bash
./mvnw test
```

## API Usage

The API provides endpoints for processing XML documents and retrieving similarity results. For detailed information, see:

- [API Specification](docs/api-specification.md): Comprehensive documentation of all API endpoints
- [Curl Testing Guide](docs/curl-testing.md): Examples of how to test the API using curl

### Basic Usage

1. Submit an XML document for processing:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @sample.xml http://localhost:8080/api/similarity -c cookie.txt
```

2. Retrieve similarity results:

```bash
curl -X GET -b cookie.txt http://localhost:8080/api/similarity/results
```

### XPath Support

The API supports XPath expressions to target specific elements in XML documents:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @sample.xml 'http://localhost:8080/api/similarity/xpath?xpath=//paragraph' -c cookie.txt
```

## Architecture

The application is built using the Quarkus framework and follows a layered architecture:

- **Resource Layer**: REST API endpoints
- **Service Layer**: Business logic for XML processing, sentence vectorization, and similarity analysis
- **Model Layer**: Data models for sentences, embeddings, and session data

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
