# Semantic Similarity API
A Quarkus-based REST API for analyzing XML documents and detecting similar text segments using vector embeddings and cosine similarity.

## Overview
This API streamlines text similarity analysis in XML content, reducing translation costs by minimizing redundant sentences. It also supports content quality and consistency by highlighting stylistic and structural overlaps.

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
