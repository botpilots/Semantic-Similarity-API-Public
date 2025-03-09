# Similarity API Specification

This document provides a comprehensive specification for the Similarity API, which processes XML documents and identifies groups of similar sentences.

## Base URL

All endpoints are relative to the base URL:

```
http://localhost:8080/api/similarity
```

## Authentication

The API uses session-based authentication with cookies. A session ID is provided when processing an XML document and must be included in subsequent requests.

## Endpoints

### 1. Process XML Document

Processes an XML document and starts the similarity analysis.

**Endpoint:** `/`

**Method:** `POST`

**Content-Type:** `application/xml`

**Request Body:** XML document content

**Example Request:**
```bash
curl -X POST \
  -H "Content-Type: application/xml" \
  --data-binary @sample.xml \
  http://localhost:8080/api/similarity
```

**Response:**
- Status Code: `202 Accepted`
- Content-Type: `application/json`
- Body: JSON object with processing status and session ID
- Cookies: `session_id` cookie containing the session identifier

**Example Response:**
```json
{
  "message": "Processing started. Results will be available for this session.",
  "error": null,
  "sessionId": "04f8f417-ce75-4e47-9333-1c147e180c75"
}
```

### 2. Process XML Document with XPath

Processes an XML document using a specific XPath expression to extract sentences from targeted elements.

**Endpoint:** `/xpath`

**Method:** `POST`

**Content-Type:** `application/xml`

**Query Parameters:**
- `xpath` (optional): XPath expression to select elements for text extraction. If omitted, defaults to `self::*` (the document element itself).

**Request Body:** XML document content

**Example Request:**
```bash
curl -X POST \
  -H "Content-Type: application/xml" \
  --data-binary @sample.xml \
  'http://localhost:8080/api/similarity/xpath?elements=paragraph'
```

**Response:**
- Status Code: `202 Accepted`
- Content-Type: `application/json`
- Body: JSON object with processing status and session ID
- Cookies: `session_id` cookie containing the session identifier

**Example Response:**
```json
{
  "message": "Processing started. Results will be available for this session.",
  "error": null,
  "sessionId": "04f8f417-ce75-4e47-9333-1c147e180c75"
}
```

### 3. Retrieve Similarity Results

Retrieves the similarity results for a previously processed XML document.

**Endpoint:** `/results`

**Method:** `GET`

**Required Cookies:**
- `session_id`: The session ID received from a previous processing request

**Example Request:**
```bash
curl -X GET \
  -b "session_id=04f8f417-ce75-4e47-9333-1c147e180c75" \
  http://localhost:8080/api/similarity/results
```

**Response:**
- Status Code: `200 OK` if results are available
- Status Code: `404 Not Found` if session not found or expired
- Status Code: `400 Bad Request` if session cookie is missing
- Content-Type: `application/json`
- Body: Array of arrays, where each inner array contains similar sentences

**Example Response:**
```json
[
  [
    "This is a test paragraph.",
    "This paragraph demonstrates how the similarity service works."
  ],
  [
    "It contains several sentences.",
    "Sentences that are similar should be placed in groups."
  ]
]
```

## Error Responses

### Invalid XML

**Response:**
- Status Code: `500 Internal Server Error`
- Content-Type: `application/json`
- Body: JSON object with error details

**Example Response:**
```json
{
  "message": "Error processing request.",
  "error": "Invalid XML: Premature end of file.",
  "sessionId": null
}
```

### Empty XML

**Response:**
- Status Code: `400 Bad Request`
- Content-Type: `application/json`
- Body: JSON object with error details

**Example Response:**
```json
{
  "message": "Error processing request.",
  "error": "XML content is empty or invalid",
  "sessionId": null
}
```

### Missing Session Cookie

**Response:**
- Status Code: `400 Bad Request`
- Content-Type: `application/json`
- Body: JSON object with error details

**Example Response:**
```json
{
  "message": "Session cookie missing or invalid.",
  "error": null
}
```

## XPath Examples

The API supports various XPath expressions to target specific elements in the XML document:

- `//paragraph`: Select all paragraph elements
- `//paragraph[1]`: Select only the first paragraph element
- `//title`: Select all title elements
- `//paragraph[position()<3]`: Select the first two paragraph elements
- `/document/content/paragraph`: Select paragraph elements that are direct children of the content element

## Notes on XPath Usage

1. When using XPath expressions with special characters in a URL, they should be URL-encoded:
   - `//paragraph[1]` becomes `%2F%2Fparagraph%5B1%5D`

2. When using curl, enclose the URL in single quotes to prevent shell interpretation of special characters:
   ```bash
   curl -X POST -H "Content-Type: application/xml" --data-binary @sample.xml 'http://localhost:8080/api/similarity/xpath?elements=paragraph'
   ```

## Processing Flow

1. Submit an XML document for processing (with or without XPath)
2. Receive a session ID in the response
3. Wait for processing to complete (asynchronous)
4. Use the session ID to retrieve similarity results

## Implementation Details

- Sentence extraction: The API extracts sentences from the XML document based on the provided XPath expression
- Sentence vectorization: Each sentence is converted to a vector embedding
- Similarity calculation: Cosine similarity is calculated between sentence vectors
- Grouping: Sentences with similarity above a threshold (default: 0.75) are grouped together 