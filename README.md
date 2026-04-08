<a id="readme-top"></a>

<div align="center">
  <img src="https://skillicons.dev/icons?i=java,spring,gradle,postgres,docker&perline=5" alt="Java, Spring Boot, Gradle, PostgreSQL, Docker" />
</div>

<br />
<div align="center">
  <h1 align="center">LumivIA Backend</h1>
  <p align="center">
    Open backend platform for real-time urban emissions intelligence, healthy routing, and flood-aware mobility decisions in CDMX.
    <br />
    <a href="./backend-lumivia"><strong>Explore backend source</strong></a>
  </p>
</div>

## About

This repository currently documents and exposes the **backend** part of LumivIA.

The service is built with Spring Boot and provides:

- Real-time vehicle detection ingestion
- Emissions estimation (CO2, NOx, PM2.5)
- WebSocket camera state updates for live maps
- Fast vs healthy route calculation with GraphHopper
- Flood risk estimation using elevation + crowdsourced reports
- GeoJSON endpoints for direct Mapbox integration

Frontend and additional components are published in separate repositories.

## Why This Is Innovative

- Combines three decision layers in one route engine: live emissions, historical emissions, and rain-triggered flood risk.
- Uses spatial interpolation (IDW) and camera influence radii instead of nearest-point shortcuts.
- Exposes map-ready contracts (`[lng, lat]` and GeoJSON) to reduce frontend coupling.
- Keeps an event-driven loop: detection -> runtime state update -> websocket broadcast -> route impact.
- Designed for real-world cloud deployment with heavy geospatial data constraints.

## Tech Stack

- Java 17
- Spring Boot 3.3
- Gradle 8
- Spring Data JPA
- PostgreSQL (default)
- H2 (dev profile)
- GraphHopper 9.1
- GeoTools + JTS
- Docker / Docker Compose

## Repository Structure

```text
LumivIA/
├── backend-lumivia/
│   ├── src/main/java/com/lumivia/
│   ├── src/main/resources/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── build.gradle
│   └── README.md
└── README.md
```

## Quick Start

1. Enter backend folder:

```bash
cd backend-lumivia
```

2. Run in development mode:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

3. Open test pages:

- `http://localhost:8080/ws-test.html`
- `http://localhost:8080/flood-test.html`
- `http://localhost:8080/mapbox-flood.html`

### PostgreSQL mode

```bash
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=lumivia \
DB_USER=postgres \
DB_PASSWORD=postgres \
./gradlew bootRun
```

### Docker mode

```bash
cd backend-lumivia
docker build -t lumivia-backend:latest .
docker run --rm -p 8080:8080 lumivia-backend:latest
```

## Required Geospatial Data

The backend needs large files that are intentionally excluded from git:

- `backend-lumivia/data/cdmx.osm.pbf`
- `backend-lumivia/data/elevation/cdmx_dem.tif`
- `backend-lumivia/data/graph-cache/` (generated on first run)

OSM source:

- https://download.geofabrik.de/north-america/mexico.html

## API Surface

- `GET /api/camaras`
- `POST /api/vehiculos/deteccion`
- `GET /api/historial`
- `POST /api/ruta`
- `GET /api/flood/risk`
- `POST /api/flood/reports`
- `GET /api/flood/reports`
- `POST /api/flood/reports/{id}/upvote`
- `GET /api/flood/geojson/grid`
- `GET /api/flood/geojson/reports`
- `GET /api/flood/geojson/bounds`

For full backend contracts and examples, see:

- `backend-lumivia/README.md`
- `backend-lumivia/http/lumivia.http`
- `backend-lumivia/postman/LumivIA-Backend.postman_collection.json`

## Open Source Positioning

LumivIA Backend is intended as an open contribution point for sustainable mobility systems:

- Transparent API contracts
- Cloud-portable deployment
- Public health + climate risk as first-class routing signals
- Modular architecture for incremental research and productization

## Contributing

1. Fork the project
2. Create a branch (`feature/your-change`)
3. Commit with clear scope
4. Open a Pull Request with context and validation notes

## License

No `LICENSE` file is currently present in this repository.

Before broad public distribution, add an explicit open source license.
