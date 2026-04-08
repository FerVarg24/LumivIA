# AGENTS.md

## Project Overview

Spring Boot 3.3 backend (Java 17, Gradle 8.10) for LumivIA: real-time vehicle emissions tracking and healthy routing in CDMX.

## Quick Commands

```bash
# Dev mode (H2 in-memory, no PostgreSQL)
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# Production mode (requires PostgreSQL + env vars)
DB_HOST=localhost DB_NAME=lumivia DB_USER=postgres DB_PASSWORD=postgres ./gradlew bootRun

# Run tests
./gradlew test

# Build
./gradlew build
```

## Critical Setup

- **OSM file required for routing**: Place `cdmx.osm.pbf` in `./data/` (download from Geofabrik). Without it, `POST /api/ruta` returns 503.
- **First startup is slow**: GraphHopper imports/indexes the OSM file on first run (several minutes).
- **JVM memory**: `bootRun` uses `-Xmx4g` (see `build.gradle:60`).

## Architecture

```
src/main/java/com/lumivia/
  camera/     # Fixed camera CRUD (GET /api/camaras)
  vehicle/    # YOLO detections, active vehicle pool with TTL
  emissions/  # CO2/NOx/PM2.5 calculation by vehicle type
  history/    # Historical queries (GET /api/historial)
  routing/    # Fast vs healthy routes with GraphHopper (POST /api/ruta)
  websocket/  # STOMP/SockJS on /ws, publishes to /topic/camaras
  common/     # Exception handlers
  flood/      # Placeholder (flood risk not yet implemented)
```

## Key Behaviors

- **Profiles**: `dev` uses H2 in-memory; default profile uses PostgreSQL via env vars.
- **Seed data**: `src/main/resources/data.sql` auto-runs on startup (inserts cameras if missing).
- **WebSocket**: Events on `/topic/camaras` for new vehicles and TTL expirations.
- **Routing bounds**: Only CDMX coordinates accepted (lat 19.0-19.7, lng -99.4 to -98.9).
- **Coordinate format**: Route responses use `[lng, lat]` (Mapbox style).

## Testing

- No tests exist in `src/test/` currently.
- Manual testing: `http/lumivia.http` (IntelliJ HTTP client) or `postman/` collection.
- WebSocket test page: `http://localhost:8080/ws-test.html` (when running).

## Dependencies

- GraphHopper 9.1 for routing
- GeoTools 31.3 for spatial operations
- PostgreSQL driver + H2 (runtime)
