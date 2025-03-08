# SemSim - Self-Contained Sentence Similarity API

This Quarkus application provides a self-contained Sentence Similarity API that processes XML documents, extracts sentences, generates vector embeddings, and groups similar sentences based on cosine similarity.

## Features

- **XML Document Processing**: Parse XML documents to extract text content.
- **Sentence Vectorization**: Convert sentences into vector embeddings using a DL4J-based model.
- **In-Memory Vector Storage**: Store vectors and text in-memory for fast processing.
- **Similarity Grouping**: Group sentences that exceed a defined similarity threshold.
- **Session Management**: Manage user sessions for asynchronous processing and result retrieval.
- **Health Checks**: Includes readiness probes for container orchestration.

## Running the application

### Development Mode

```shell
./mvnw compile quarkus:dev
```

This enables hot reload and live coding. The application will be accessible at http://localhost:8080.

### Packaging and Running in JVM Mode

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### Creating a Docker Container

```shell
./mvnw package
docker build -f src/main/docker/Dockerfile.jvm -t quarkus/semsim-jvm .
docker run -i --rm -p 8080:8080 quarkus/semsim-jvm
```

## Configuration

Key configuration parameters in `application.properties`:

- `semsim.similarity.threshold`: The cosine similarity threshold for grouping (default: 0.75)
- `semsim.session.timeout.minutes`: Session timeout in minutes (default: 60)

## API Endpoints

### Submit XML for Processing

```
POST /api/similarity
Content-Type: application/xml

<xml>Your XML content here</xml>
```

**Response**: 
- Status: 202 Accepted
- Sets a session cookie: `session_id`
- JSON body with a message and session ID.

### Retrieve Results

```
GET /api/similarity/results
Cookie: session_id=your-session-id
```

**Response**:
- Status: 200 OK
- JSON array of sentence groups: `[["sentence 1", "sentence 2"], ["sentence 3", "sentence 4"]]`

## Health Checks

The application provides a health check endpoint at `/q/health/ready`.

## Testing

### Sample XML

Here's a simple XML document you can use for testing:

```xml
<document>
    <title>Sample Document</title>
    <content>
        <paragraph>
            This is a test paragraph. It contains several sentences.
            Some sentences are similar to each other. Similar sentences should be grouped together.
        </paragraph>
        <paragraph>
            This test paragraph has sentences. Sentences that are similar should be placed in groups.
            Completely different sentences like this one should be separate.
        </paragraph>
    </content>
</document>
```

### Using cURL

```shell
# Submit XML for processing
curl -X POST -H "Content-Type: application/xml" \
  -d @sample.xml \
  -c cookies.txt \
  http://localhost:8080/api/similarity

# Retrieve results
curl -X GET -b cookies.txt http://localhost:8080/api/similarity/results
```

## Implementation Notes

- This is a simplified implementation that uses a mock embedding model for demonstration purposes.
- In a production environment, you would integrate a pre-trained sentence transformer model.
- For large XML documents, consider implementing pagination or limiting the number of sentences processed.
