# Multi-stage Dockerfile for LogDate Server
# Optimized for both local development and Google Cloud Run production deployment

# Build stage
FROM gradle:8.10.2-jdk17 AS build

WORKDIR /app

# Copy gradle files first for better caching
COPY gradle/ gradle/
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml

# Copy project structure and build files
COPY build.gradle.kts ./
COPY shared/ shared/
COPY client/util/ client/util/
COPY server/ server/

# Build the application
RUN ./gradlew :server:build -x test --no-daemon

# Development stage (for docker-compose)
FROM openjdk:17-jdk-slim AS development

WORKDIR /app

# Install development tools
RUN apt-get update && apt-get install -y \
    curl \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Copy built application
COPY --from=build /app/server/build/libs/*.jar app.jar

# Development environment variables
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV KTOR_DEVELOPMENT=true

# Health check for development
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Production stage (for Cloud Run)
FROM openjdk:17-jdk-slim AS production

WORKDIR /app

# Create non-root user for security
RUN groupadd -r logdate && useradd -r -g logdate logdate

# Install minimal required packages
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Copy built application
COPY --from=build /app/server/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R logdate:logdate /app
USER logdate

# Production JVM optimizations for Cloud Run
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

# Cloud Run configuration
ENV PORT=8080
ENV HOST=0.0.0.0
ENV KTOR_DEVELOPMENT=false

# Health check for production
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:$PORT/health || exit 1

EXPOSE $PORT

# Use exec form for better signal handling
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]