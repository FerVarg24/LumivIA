# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S lumivia && \
    adduser -u 1001 -S lumivia -G lumivia

# Copy the built JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# Copy data files (OSM, elevation, graph-cache)
# These are required for GraphHopper routing
COPY data/ /app/data/

# Set ownership
RUN chown -R lumivia:lumivia /app

USER lumivia

# JVM memory settings for GraphHopper
ENV JAVA_OPTS="-Xmx4g -Xms1g"

# Spring Boot config to use /app/data
ENV LUMIVIA_OSM_FILE=/app/data/cdmx.osm.pbf
ENV LUMIVIA_GRAPH_CACHE=/app/data/graph-cache
ENV LUMIVIA_FLOOD_DEM_FILE=/app/data/elevation/cdmx_dem.tif

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
