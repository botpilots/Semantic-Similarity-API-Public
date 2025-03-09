#!/bin/bash

# Semantic Similarity API - Curl Testing Script
# This script contains various curl commands to test the Similarity API
# Based on the examples from docs/curl-testing.md

# Default to small test suite
TEST_SUITE="small"

# Check for command line arguments
for arg in "$@"; do
	case $arg in
	--large)
		TEST_SUITE="large"
		shift # Remove --large from processing
		;;
	*)
		# Unknown option
		;;
	esac
done

# Set the base URL for the API
BASE_URL="http://localhost:8080"
COOKIE_FILE="cookie.txt"

# Function to display section headers
section() {
	echo ""
	echo "============================================================"
	echo "  $1"
	echo "============================================================"
	echo ""
}

# Function to run a curl command and display the command and its output
run_curl() {
	echo "$ $1"
	eval "$1"
	echo ""
}

# Function to check if we should run a test based on the selected test suite
should_run() {
	local suite=$1
	if [ "$TEST_SUITE" = "large" ] || [ "$suite" = "small" ]; then
		return 0 # True - run the test
	else
		return 1 # False - skip the test
	fi
}

section "Test Configuration"
echo "Running test suite: $TEST_SUITE"
echo "Base URL: $BASE_URL"
echo "Cookie file: $COOKIE_FILE"

section "Trigger Accepted GET response Example"

# 1. Submit an XML document for processing
run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_xs.xml ${BASE_URL}/api/similarity -c ${COOKIE_FILE}"

# 2. Retrieve similarity results
run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"

section "Sample File Tests"

# Extra Small Sample (XML) - part of small suite
if should_run "small"; then
	echo "Running: Extra Small Sample (XML)"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_xs.xml ${BASE_URL}/api/similarity -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

# Small Sample (DITA) - part of small suite
if should_run "small"; then
	echo "Running: Small Sample (DITA)"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_s.dita ${BASE_URL}/api/similarity/ -c ${COOKIE_FILE}"
	echo "Waiting 2 seconds for processing..."
	sleep 2
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

# Medium Sample (DITA) - part of large suite only
if should_run "large"; then
	echo "Running: Medium Sample (DITA)"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_m.dita ${BASE_URL}/api/similarity -c ${COOKIE_FILE}"
	echo "Waiting 2 seconds for processing..."
	sleep 2
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

# Large Sample (DITA) - part of large suite only
if should_run "large"; then
	echo "Running: Large Sample (DITA)"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_l.dita ${BASE_URL}/api/similarity -c ${COOKIE_FILE}"
	echo "Waiting 3 seconds for processing..."
	sleep 3
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

section "XPath Tests"

# Process XML with XPath - Extra Small Sample - part of small suite
if should_run "small"; then
	echo "Running: XPath with Extra Small Sample"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_xs.xml '${BASE_URL}/api/similarity?xpath=%2F%2Fparagraph' -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

# Process XML with XPath - Small Sample (DITA) - part of small suite
if should_run "small"; then
	echo "Running: XPath with Small Sample"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_s.dita '${BASE_URL}/api/similarity?xpath=%2F%2Fp' -c ${COOKIE_FILE}"
	echo "Waiting 2 seconds for processing..."
	sleep 2
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

# Process XML with XPath - Medium Sample (DITA) - part of large suite only
if should_run "large"; then
	echo "Running: XPath with Medium Sample"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_m.dita '${BASE_URL}/api/similarity?xpath=%2F%2Ftitle' -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

# Process XML with XPath - Large Sample (DITA) - part of large suite only
if should_run "large"; then
	echo "Running: XPath with Large Sample"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_l.dita '${BASE_URL}/api/similarity?xpath=%2F%2Fli%2Fp' -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

section "Advanced XPath Examples"

# These examples use the extra small sample for demonstration
if should_run "small"; then
	# Select all paragraph elements
	echo "Running: XPath - Select all paragraphs"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_xs.xml '${BASE_URL}/api/similarity?xpath=%2F%2Fparagraph' -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

if should_run "large"; then
	# Select only the first paragraph element (using URL encoding to avoid issues with square brackets)
	echo "Running: XPath - Select first paragraph"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_xs.xml '${BASE_URL}/api/similarity?xpath=%2F%2Fparagraph%5B1%5D' -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

section "URL-encoded XPath Examples"

if should_run "small"; then
	echo "NOTE: The URL-encoded XPath should not capture any elements and result in empty GET."
	# URL-encoded example for //paragraph[contains(text(),'example')]
	echo "Running: URL-encoded XPath - Text content search"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_xs.xml '${BASE_URL}/api/similarity?xpath=%2F%2Fparagraph%5Bcontains%28text%28%29%2C%27example%27%29%5D' -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

if should_run "large"; then
	# URL-encoded example for //paragraph[string-length() > 100]
	echo "Running: URL-encoded XPath - String length function"
	run_curl "curl -X POST -H \"Content-Type: application/xml\" --data-binary @samples/sample_xs.xml '${BASE_URL}/api/similarity?xpath=%2F%2Fparagraph%5Bstring-length%28%29%20%3E%20100%5D' -c ${COOKIE_FILE}"
	echo "Waiting 1 second for processing..."
	sleep 1
	run_curl "curl -X GET -b ${COOKIE_FILE} ${BASE_URL}/api/similarity/results"
fi

section "Troubleshooting"

# Display cookie file contents
run_curl "cat ${COOKIE_FILE}"

# Calculate and display total execution time
if [ "$TEST_SUITE" = "small" ]; then
	echo "Small test suite completed."
else
	echo "Large test suite completed."
fi

echo ""
echo "All tests completed!"
