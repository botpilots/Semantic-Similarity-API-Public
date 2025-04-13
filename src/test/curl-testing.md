# Testing the Similarity API with curl

This document provides examples of how to test the Similarity API using curl commands.

## Example Curl Commands

### 1. Process a Small XML Document (without XPath)

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_s.dita http://localhost:8080/api/similarity -c cookie.txt
```

### 2. Process a Medium XML Document (with encoded XPath)

```bash
curl -X POST -H "Content-Type: application/xml" --data-binary @samples/sample_m.dita 'http://localhost:8080/api/similarity?elements=title' -c cookie.txt
```

### 3. Retrieve Similarity Results

```bash
curl -X GET -b cookie.txt http://localhost:8080/api/similarity/results
```

## Sample Files Examples

See sample folder for different sample files.

## Cookie File Format

The cookie file created by curl follows the Netscape cookie file format. You can inspect it with:

```bash
cat cookie.txt
```