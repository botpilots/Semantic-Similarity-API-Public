#!/bin/bash

# Script to kill all Quarkus and Maven processes
# Usage: ./kill-quarkus-maven.sh

echo "Searching for Quarkus and Maven processes..."

# Find all Java processes
JAVA_PROCESSES=$(jps -l)

# Initialize arrays to store PIDs
MAVEN_PIDS=()
QUARKUS_PIDS=()
OTHER_JAVA_PIDS=()

# Process each line of the output
while IFS= read -r line; do
	PID=$(echo "$line" | awk '{print $1}')
	PROCESS=$(echo "$line" | awk '{$1=""; print $0}' | xargs)

	# Skip jps itself
	if [[ "$PROCESS" == *"jps"* ]]; then
		continue
	fi

	# Categorize processes
	if [[ "$PROCESS" == *"maven"* ]]; then
		MAVEN_PIDS+=("$PID")
		echo "Found Maven process: $PID - $PROCESS"
	elif [[ "$PROCESS" == *"quarkus"* ]] || [[ "$PROCESS" == *"semsim"* ]]; then
		QUARKUS_PIDS+=("$PID")
		echo "Found Quarkus process: $PID - $PROCESS"
	elif [[ "$PROCESS" == *".jar"* ]]; then
		# Check if it's a Quarkus dev mode jar
		if [[ "$PROCESS" == *"semsim-dev.jar"* ]]; then
			QUARKUS_PIDS+=("$PID")
			echo "Found Quarkus dev mode process: $PID - $PROCESS"
		else
			OTHER_JAVA_PIDS+=("$PID")
			echo "Found other Java process: $PID - $PROCESS"
		fi
	fi
done <<<"$JAVA_PROCESSES"

# Combine all PIDs
ALL_PIDS=("${MAVEN_PIDS[@]}" "${QUARKUS_PIDS[@]}")

# Kill processes if any were found
if [ ${#ALL_PIDS[@]} -eq 0 ]; then
	echo "No Quarkus or Maven processes found."
else
	echo "Killing ${#ALL_PIDS[@]} processes..."
	for PID in "${ALL_PIDS[@]}"; do
		echo "Killing process $PID"
		kill -9 "$PID" 2>/dev/null
	done
	echo "All Quarkus and Maven processes have been terminated."
fi

# Verify all processes are killed
REMAINING=$(jps -l | grep -E 'maven|quarkus|semsim-dev.jar' | grep -v "jps")
if [ -n "$REMAINING" ]; then
	echo "Warning: Some processes may still be running:"
	echo "$REMAINING"
else
	echo "Verification complete: All processes successfully terminated."
fi
