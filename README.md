# Semantic Similarity API

A Quarkus-based REST API for processing XML documents and identifying groups of similar text elements using vector embeddings and cosine similarity.

## Introduction

The Semantic Similarity API provides a powerful solution for analyzing text similarity within XML documents. This enables cost-savings for large translation projects, as fewer sentences can be sent to the translation agency. It also reinforces analysis of document integrity and style, and thus works as a tool to improve content quality.

### Features

- **Element Selection**: Target specific elements in XML documents using space-separated element names
- **Text Vectorization**: Convert extracted text to vector embeddings
- **Similarity Analysis**: Group similar text based on cosine similarity
- **Asynchronous Processing**: Process documents asynchronously with session-based result retrieval

## Getting Started

### Prerequisites

- Java 11 or higher
- Maven 3.8.1 or higher

### Running the Application

```bash
./mvnw compile quarkus:dev
```

The application will be available at http://localhost:8080.

To test and demo the APIs capabilities, run script curl-testing.sh or see: [Curl Testing Guide](src/test/curl-testing.md).

## Documentation

For detailed information about the API, please refer to the documentation in the `docs` folder: [API Specification](docs/api-specification.md).

## License

MIT License.
