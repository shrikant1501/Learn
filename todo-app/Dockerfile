# ================================================================
# Stage 1: BUILD
# ================================================================
# We use a multi-stage build — the build stage compiles the app,
# and the final image only contains the compiled JAR (no JDK, no Maven).
# This keeps the production image small and secure.
#
# eclipse-temurin:21-jdk-alpine — JDK 21 (LTS) on Alpine Linux.
# Alpine is ~5MB vs ~200MB for full Ubuntu-based images.
# We need JDK here (not JRE) because we're compiling with Maven.
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy pom.xml first — Docker caches layers.
# If pom.xml hasn't changed, Docker reuses the cached dependency layer
# on the next build (much faster on rebuilds — deps don't re-download).
COPY pom.xml .

# Download dependencies into the Docker layer cache.
# The -q flag reduces output noise. The 'dependency:go-offline' goal
# downloads everything needed so the actual build has no network calls.
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q

# Now copy source code — this layer only invalidates when source changes
COPY src ./src

# Build the application JAR, skipping tests.
# Tests run in the CI pipeline and via 'mvn test' locally — not in the image build.
# The -q flag reduces Maven output noise.
RUN mvn package -DskipTests -q

# ================================================================
# Stage 2: RUNTIME
# ================================================================
# The final image uses JRE (not JDK) — no compiler needed at runtime.
# eclipse-temurin:21-jre-alpine is ~80MB vs ~200MB for the JDK image.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security.
# Running as root inside a container is a security anti-pattern —
# if the process is compromised, the attacker has root in the container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy ONLY the compiled JAR from the builder stage.
# Nothing else from Stage 1 (JDK, Maven, source code) ends up in the final image.
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to the non-root user
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# The port Spring Boot listens on (must match server.port in application.properties)
EXPOSE 8080

# JVM flags:
#   -XX:+UseContainerSupport     → JVM reads memory/CPU limits from Docker cgroups
#   -XX:MaxRAMPercentage=75.0    → JVM heap = 75% of container RAM (not 25% default)
#   -Djava.security.egd=...      → faster startup on Linux (avoids /dev/random blocking)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
