# Troubleshooting Quarkus Native Docker Builds on Apple Silicon (ARM64)

This document outlines common issues encountered when building and running Quarkus native applications in Docker containers on Apple Silicon (ARM64) Macs and their solutions.

## Problem 1: `exec format error`

**Symptoms:**

When running the Docker container (`docker run ...`), you see an error similar to:

```
exec ./application: exec format error
```

**Cause:**

This indicates an architecture mismatch between the native executable inside the container and the container's operating system/architecture. Typically, this happens when:

1.  You build the native executable directly on your ARM64 Mac (`./mvnw package -Dnative`) without specifying a target platform. This creates a *macOS ARM64* executable (`Mach-O 64-bit executable arm64`).
2.  You copy this macOS executable into a *Linux ARM64* Docker container (e.g., based on `ubi9`).
3.  The Linux environment inside the container cannot execute the macOS binary format, even though both are ARM64.

**Solution: Use Container Build**

Force Quarkus to build a *Linux* native executable using its container build feature. This performs the native compilation step inside a dedicated Docker container that matches the target Linux environment.

1.  **Clean previous build (optional but recommended):**
    ```bash
    rm -rf target
    ```
2.  **Build using container build:**
    ```bash
    ./mvnw package -Dnative -Dquarkus.native.container-build=true
    ```
    This uses a builder image (e.g., `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21`) to compile the executable for Linux ARM64 (`ELF 64-bit LSB executable, ARM aarch64`).
3.  **Build the application Docker image:** Use your standard Dockerfile (e.g., `src/main/docker/Dockerfile.native-micro`) to build the image. This will copy the *correct* Linux executable into the container.
    ```bash
    docker build -f src/main/docker/Dockerfile.native-micro -t your-image-name .
    ```

## Problem 2: GLIBC Version Mismatch

**Symptoms:**

After resolving the `exec format error` using the container build, you might encounter errors like this when running the container:

```
./application: /lib64/libc.so.6: version `GLIBC_2.33' not found (required by ./application)
./application: /lib64/libc.so.6: version `GLIBC_2.32' not found (required by ./application)
./application: /lib64/libc.so.6: version `GLIBC_2.34' not found (required by ./application)
```

**Cause:**

This means the native executable, built inside the builder container (which uses a specific GLIBC version, e.g., from UBI 9), requires a newer version of the GLIBC library than what is available in the *runtime* base image specified in your application's Dockerfile.

For example, the builder might use UBI 9, but your runtime image (`FROM ...` line in your Dockerfile) might be based on an older distribution or an image with an older GLIBC.

**Solution: Align Runtime Base Image**

Ensure the base image used in your application's Dockerfile (`src/main/docker/Dockerfile.native-micro`) is compatible with the build environment.

1.  **Identify the builder environment:** The Maven build log usually shows the builder image used (e.g., `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21`). Note the base OS (e.g., UBI 9).
2.  **Update Dockerfile:** Modify the `FROM` line in your `Dockerfile.native-micro` to use a compatible minimal base image. Using the official UBI minimal images is often a good choice:
    *   **For UBI 9:**
        ```dockerfile
        # FROM quay.io/quarkus/quarkus-micro-image:2.0  # Or whatever was causing the issue
        FROM registry.access.redhat.com/ubi9/ubi-minimal
        ```
    *   **For UBI 8 (if you had to use a UBI 8 builder):**
        ```dockerfile
        FROM registry.access.redhat.com/ubi8/ubi-minimal
        ```
3.  **Rebuild the application Docker image:**
    ```bash
    docker build -f src/main/docker/Dockerfile.native-micro -t your-image-name .
    ```
4.  **Run the container:**
    ```bash
    docker run -i --rm -p 8080:8080 your-image-name
    ```

By ensuring the native executable is built *for* Linux (using container build) and the runtime container uses a compatible Linux base image (like UBI 9 minimal), these common issues can be resolved.
