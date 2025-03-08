# Testing the Similarity API with curl

This document provides examples of how to test the Similarity API using curl commands, with a focus on handling session cookies.

## Basic API Testing

The Similarity API provides endpoints for processing XML documents and retrieving similarity results. The API uses cookies to maintain session state between requests.

### Prerequisites

- curl installed on your system
- A running instance of the Similarity API (default: http://localhost:8080)
- Sample XML files for testing (available in the `samples` directory)

## Basic Usage

The simplest workflow for using the API involves two steps:

1. Submit an XML document for processing:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_xs.xml http://localhost:8080/api/similarity -c cookie.txt
```

2. Retrieve similarity results:

```bash
curl -X GET -b cookie.txt http://localhost:8080/api/similarity/results
```

## Testing Workflow

### 1. Process an XML Document

To process an XML document, send a POST request to the `/api/similarity` endpoint:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_xs.xml http://localhost:8080/api/similarity -c cookie.txt -v
```

This command:
- Sends a POST request with the contents of `sample.xml`
- Sets the Content-Type header to `application/xml`
- Saves the response cookies to `cookie.txt` (-c flag)
- Shows verbose output (-v flag)

The response will include a session ID in both the response body and as a cookie.

### 2. Retrieve Similarity Results

To retrieve the similarity results, send a GET request to the `/api/similarity/results` endpoint with the session cookie:

```bash
curl -X GET -b cookie.txt http://localhost:8080/api/similarity/results -v
```

This command:
- Sends a GET request to the results endpoint
- Includes the cookies from `cookie.txt` (-b flag)
- Shows verbose output (-v flag)

The response will contain groups of similar sentences from the processed XML.

## Sample Files Examples

The project includes several sample files of different sizes and structures that you can use for testing:

### Extra Small Sample (XML)

Process the extra small XML sample without XPath:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_xs.xml http://localhost:8080/api/similarity -c cookie.txt -v
```

### Small Sample (DITA)

Process the small DITA sample with the `self::*` XPath expression (selects all nodes):

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_s.dita http://localhost:8080/api/similarity/ -c cookie.txt -v
```

### Medium Sample (DITA)

Process the medium DITA sample without XPath:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_m.dita http://localhost:8080/api/similarity -c cookie.txt -v
```

### Large Sample (DITA)

Process the large DITA sample without XPath (note: this may take longer to process due to file size):

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_l.dita http://localhost:8080/api/similarity -c cookie.txt -v
```

## Advanced Testing: Using XPath

The API supports an optional XPath parameter to extract sentences from specific XML elements.

### Process XML with XPath

To process an XML document with an XPath expression, use the `/api/similarity/xpath` endpoint:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @sample.xml 'http://localhost:8080/api/similarity/xpath?xpath=//paragraph' -c cookie.txt -v
```

Note the single quotes around the URL to prevent shell interpretation of special characters in the XPath expression.

For more complex XPath expressions, URL-encode the expression:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @sample.xml 'http://localhost:8080/api/similarity/xpath?xpath=%2F%2Fparagraph%5B1%5D' -c cookie.txt -v
```

This example uses the URL-encoded form of `//paragraph[1]` to select only the first paragraph.

### XPath Examples with Different Sample Files

#### Extra Small Sample (XML)

Extract all paragraphs from the extra small sample:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_xs.xml 'http://localhost:8080/api/similarity/xpath?xpath=//paragraph' -c cookie.txt -v
```

#### Small Sample (DITA)

Extract all paragraphs from the small DITA sample:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_s.dita 'http://localhost:8080/api/similarity/xpath?xpath=//p' -c cookie.txt -v
```

#### Medium Sample (DITA)

Extract all titles from the medium DITA sample:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_m.dita 'http://localhost:8080/api/similarity/xpath?xpath=//title' -c cookie.txt -v
```

#### Large Sample (DITA)

Extract all list items from the large DITA sample:

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_l.dita 'http://localhost:8080/api/similarity/xpath?xpath=//li/p' -c cookie.txt -v
```

### Common XPath Examples

Here are some useful XPath expressions for testing:

- `//paragraph` - Select all paragraph elements
- `//paragraph[1]` - Select only the first paragraph element
- `//title` - Select all title elements
- `//paragraph[position()<3]` - Select the first two paragraph elements
- `/document/content/paragraph` - Select paragraph elements that are direct children of the content element
- `//p` - Select all p elements (common in DITA files)
- `//li/p` - Select all p elements that are direct children of li elements
- `//body/p` - Select all p elements that are direct children of body elements

## Troubleshooting

### Special Characters in XPath

If your XPath expression contains special characters like `[]`, `()`, or `//`, you may need to:

1. URL-encode the expression:
   - `//paragraph[1]` becomes `%2F%2Fparagraph%5B1%5D`
   
2. Use single quotes around the URL to prevent shell interpretation:
   ```bash
   curl -X POST -H "Content-Type: application/xml" --data-binary @sample.xml 'http://localhost:8080/api/similarity/xpath?xpath=//paragraph' -c cookie.txt
   ```

### Cookie File Format

The cookie file created by curl follows the Netscape cookie file format. You can inspect it with:

```bash
cat cookie.txt
```

Example output:
```
# Netscape HTTP Cookie File
# https://curl.se/docs/http-cookies.html
# This file was generated by libcurl! Edit at your own risk.

#HttpOnly_localhost     FALSE   /       FALSE   0       session_id      04f8f417-ce75-4e47-9333-1c147e180c75
```

## Automated Testing

While curl is useful for manual testing, consider using the automated tests in the project for regression testing:

- `XmlProcessorServiceTest` - Tests the XPath functionality at the service level
- `SimilarityResourceTest` - Tests the API endpoints, including the XPath parameter

Run the tests with:

```bash
./mvnw test
``` 