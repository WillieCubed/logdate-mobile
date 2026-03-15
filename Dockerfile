# syntax=docker/dockerfile:1.7-labs
# Multi-stage Dockerfile for LogDate Server
# Optimized for both local development and Google Cloud Run production deployment

# Build stage
FROM gradle:8.10.2-jdk17 AS build

WORKDIR /app
ENV GRADLE_USER_HOME=/home/gradle/.gradle

# Copy gradle files first for better caching
COPY --link gradle/ gradle/
COPY --link gradlew gradlew.bat gradle.properties settings.gradle.kts ./
COPY --link gradle/libs.versions.toml gradle/libs.versions.toml
COPY --link build-logic/ build-logic/

# Copy project structure and build files
COPY --link build.gradle.kts ./
COPY --link app/ app/
COPY --link client/ client/
COPY --link integration/ integration/
COPY --link samples/ samples/
COPY --link shared/ shared/
COPY --link server/ server/

# Build the application with cached Gradle dependencies
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew :server:build -x test --no-daemon --build-cache

# Development stage (for docker-compose)
FROM openjdk:17-jdk-slim AS development

WORKDIR /app

# Install development tools
RUN apt-get update && apt-get install -y \
    curl \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Copy built application
COPY --link --from=build /app/server/build/libs/logdate-server.jar logdate-server.jar

# Development environment variables
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV KTOR_DEVELOPMENT=true

# Health check for development
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

EXPOSE 8080

CMD ["java", "-jar", "logdate-server.jar"]

# Production stage (for Cloud Run)
FROM eclipse-temurin:17-jre-jammy AS production

WORKDIR /app

# Create non-root user for security
RUN groupadd -r logdate \
    && useradd -r -g logdate -d /app -s /usr/sbin/nologin logdate

# Copy built application with locked-down permissions
COPY --link --chown=logdate:logdate --chmod=0444 \
    --from=build /app/server/build/libs/logdate-server.jar /app/logdate-server.jar
USER logdate

# Production JVM optimizations for Cloud Run
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom -Djava.io.tmpdir=/tmp"

# Cloud Run configuration
ENV PORT=8080
ENV HOST=0.0.0.0
ENV KTOR_DEVELOPMENT=false

EXPOSE $PORT

# Use exec form for better signal handling
ENTRYPOINT ["java"]
CMD ["-jar", "/app/logdate-server.jar"]
