# Similarity API Specification

This document provides a comprehensive specification for the Similarity API, which processes XML documents and identifies groups of similar sentences.

## Base URL

All endpoints are relative to the base URL:

```
http://localhost:8080/api/similarity
```

## Authentication

The API uses session-based authentication with cookies. A session ID is provided when processing an XML document and must be included in subsequent requests via the `session_id` cookie.

## Endpoints

### 1. Process XML Document

Processes an XML document, extracts text from specified elements, and starts the similarity analysis.

**Endpoint:** `/`

**Method:** `POST`

**Content-Type:** `application/xml`

**Query Parameters:**
- `elements` (optional, string): A space-separated string of XML element names to extract text from. Defaults to `"p"`. Element names must be valid XML names (start with a letter or underscore, followed by letters, digits, hyphens, or underscores). Example: `elements=paragraph section title`. URL encoding might be necessary for special characters if used directly in a URL, though typically handled by HTTP clients.

**Request Body:** XML document content

**Example Request:**
```bash
curl -X POST \
  -H "Content-Type: application/xml" \
  --data-binary @sample.xml \
  'http://localhost:8080/api/similarity?elements=paragraph%20li'
```

**Response:**
- Status Code: `202 Accepted`
- Content-Type: `application/json`
- Body: JSON object (`ApiResponse`) with processing status and session ID
- Cookies: `session_id` cookie containing the session identifier

**Example Response (`202 Accepted`):**
```json
{
  "message": "Processing started. Results will be available for this session.",
  "error": null,
  "sessionId": "04f8f417-ce75-4e47-9333-1c147e180c75",
  "data": null
}
```

### 2. Retrieve Similarity Results

Retrieves the similarity results for a previously processed XML document using the session ID. This endpoint supports polling; if processing is ongoing, it returns `202 Accepted`.

**Endpoint:** `/results`

**Method:** `GET`

**Required Cookies:**
- `session_id`: The session ID received from the processing request (`/`)

**Example Request:**
```bash
curl -X GET \
  -b "session_id=04f8f417-ce75-4e47-9333-1c147e180c75" \
  http://localhost:8080/api/similarity/results
```

**Responses:**
- **`200 OK`:** Processing completed successfully. The `data` field contains the similarity groups.
  - Content-Type: `application/json`
  - Body: `ApiResponse` object. The `data` field is an array of arrays, where each inner array contains similar sentences. If no groups were found, `data` might be an empty array or null, indicated by the `message`.
- **`202 Accepted`:** Processing is still in progress. Poll again later.
  - Content-Type: `application/json`
  - Body: `ApiResponse` object with a relevant message.
- **`400 Bad Request`:** Missing or invalid `session_id` cookie, invalid session ID format (not UUID), or no text could be extracted (e.g., `elements` parameter didn't match any elements in the XML).
  - Content-Type: `application/json`
  - Body: `ApiResponse` object with error details.
- **`404 Not Found`:** The session ID is valid but no corresponding session was found (e.g., expired or never existed).
  - Content-Type: `application/json`
  - Body: `ApiResponse` object with error details.
- **`500 Internal Server Error`:** An unexpected error occurred during processing or retrieval.
  - Content-Type: `application/json`
  - Body: `ApiResponse` object with error details.

**Example Response (`200 OK` - Results Available):**
```json
{
  "message": "Processing completed. Similarity groups are available.",
  "error": null,
  "sessionId": "04f8f417-ce75-4e47-9333-1c147e180c75",
  "data": [
    [
      "This is a test paragraph.",
      "This paragraph demonstrates how the similarity service works."
    ],
    [
      "It contains several sentences.",
      "Sentences that are similar should be placed in groups."
    ]
  ]
}
```

**Example Response (`200 OK` - No Groups Found):**
```json
{
  "message": "Processing completed but no similarity groups were created for this session.",
  "error": null,
  "sessionId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "data": []
}
```

**Example Response (`202 Accepted` - Processing):**
```json
{
  "message": "Processing in progress. Please try again later.",
  "error": null,
  "sessionId": "04f8f417-ce75-4e47-9333-1c147e180c75",
  "data": null
}
```


## Error Responses (`ApiResponse` Format)

Error responses generally follow the `ApiResponse` structure. The `message` might provide user-friendly information, while `error` contains more technical details. `sessionId` may or may not be present depending on the context of the error.

**Example: Invalid `elements` Parameter (`400 Bad Request` on `/`)**
```json
{
  "message": "Elements parameter validation failed.",
  "error": "Elements parameter should be a space separated string of valid XML elements.",
  "sessionId": null,
  "data": null
}
```

**Example: Empty XML Content (`400 Bad Request` on `/`)**
```json
{
  "message": "Error processing request.",
  "error": "XML content is empty or invalid",
  "sessionId": null,
  "data": null
}
```

**Example: Missing Session Cookie (`400 Bad Request` on `/results`)**
```json
{
  "message": null,
  "error": "Session cookie was never sent.",
  "sessionId": null,
  "data": null
}
```

**Example: Invalid Session ID Format (`400 Bad Request` on `/results`)**
```json
{
  "message": null,
  "error": "Invalid session ID, not UUID format.",
  "sessionId": "invalid-session-id-format",
  "data": null
}
```

**Example: No Text Extracted (`400 Bad Request` on `/results`)**
```json
{
  "message": "No embeddings were generated. This may be because no matching elements were found in your XML. The default element is 'p'. If your XML uses different elements, please specify them using the 'elements' query parameter, for example: /api/similarity?elements=paragraph",
  "error": "No sentences found in XML. Revise elements query parameter or check data.",
  "sessionId": "f4e8a1c5-b3d9-4e8a-a1c5-b3d9a1c5b3d9",
  "data": null
}
```

**Example: Session Not Found (`404 Not Found` on `/results`)**
```json
{
  "message": null,
  "error": "No session found for ID: a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "sessionId": null, // Or the ID searched for, depending on implementation nuance
  "data": null
}
```

**Example: Processing Error (`500 Internal Server Error` on `/results`)**
```json
{
  "message": "An error occurred during processing.",
  "error": null, // Specific error might be logged server-side
  "sessionId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
  "data": null
}
```

**Example: Internal Server Error during XML Parsing (`500 Internal Server Error` on `/`)**
```json
{
  "message": null,
  "error": "Internal Server Error: ProcessingException: Underlying I/O error", // Example error
  "sessionId": null,
  "data": null
}
```

## Processing Flow

1.  Submit an XML document via `POST /` with optional `elements` query parameter.
2.  Receive a `202 Accepted` response with a `session_id` (in JSON body and cookie).
3.  Periodically poll `GET /results` using the `session_id` cookie.
4.  If `GET /results` returns `202 Accepted`, continue polling.
5.  If `GET /results` returns `200 OK`, retrieve the similarity groups from the `data` field in the JSON response.
6.  Handle potential error responses (`4xx`, `5xx`) appropriately during polling or initial submission.

## Implementation Details

-   Sentence extraction: The API extracts text content from the specified XML elements (via the `elements` parameter, defaulting to `p`) and splits it into sentences.
-   Sentence vectorization: Each sentence is converted to a vector embedding.
-   Similarity calculation: Cosine similarity is calculated between sentence vectors.
-   Grouping: Sentences with similarity above a threshold (implementation detail, e.g., 0.75) are grouped together. 