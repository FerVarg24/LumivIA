<a id="readme-top"></a>

<div align="center">
  <img src="https://skillicons.dev/icons?i=java,spring,gradle,postgres,docker&perline=5" alt="Java, Spring Boot, Gradle, PostgreSQL, Docker" />
</div>

<br />
<div align="center">
  <h1 align="center">LumivIA Backend</h1>

  <p align="center">
    Spring Boot backend for real-time urban emissions intelligence, healthy routing, and flood-aware mobility decisions in CDMX.
    <br />
    <a href="https://github.com/FerVarg24/LumivIA/tree/main/backend-lumivia"><strong>Explore the backend code</strong></a>
    <br />
    <br />
    <a href="https://github.com/FerVarg24/LumivIA/issues">Report Bug</a>
    ·
    <a href="https://github.com/FerVarg24/LumivIA/issues">Request Feature</a>
  </p>
</div>

## Table of Contents

- [About The Project](#about-the-project)
  - [Why this backend is innovative](#why-this-backend-is-innovative)
  - [Scope](#scope)
- [Built With](#built-with)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Required data files](#required-data-files)
  - [Configuration](#configuration)
- [Usage](#usage)
  - [Run in development mode](#run-in-development-mode)
  - [Run with PostgreSQL](#run-with-postgresql)
  - [Run with Docker](#run-with-docker)
  - [Run with Docker Compose](#run-with-docker-compose)
- [API Overview](#api-overview)
  - [Core endpoints](#core-endpoints)
  - [Example requests](#example-requests)
  - [WebSocket contract](#websocket-contract)
- [Project Structure](#project-structure)
- [Deployment Notes](#deployment-notes)
- [Open Source Vision](#open-source-vision)
- [Contributing](#contributing)
- [Roadmap](#roadmap)
- [License](#license)
- [Acknowledgments](#acknowledgments)

## About The Project

LumivIA Backend is the decision engine behind a city mobility platform focused on public health and risk-aware routing.

It ingests vehicle detections in real time, estimates pollutants (CO2, NOx, PM2.5), maintains live camera state over WebSocket, and computes two route options between origin and destination:

- Fast route optimized for travel time.
- Healthy route optimized for lower exposure, and flood risk when rain is active.

The backend is intentionally documented as an independent product. Frontend and companion services are maintained in separate repositories.

### Why this backend is innovative

- Fuses three data horizons in one routing decision: live emissions, historical emissions, and terrain/rain flood risk.
- Applies interpolation (IDW) over camera influence zones instead of naive nearest-camera assumptions.
- Exposes map-ready outputs (`[lng, lat]` coordinates and GeoJSON layers) to reduce frontend glue code.
- Supports a real-time mobility loop: detection event -> emissions update -> websocket broadcast -> route recalculation.
- Balances practical deployment with heavy geospatial workloads (GraphHopper + DEM + cloud runtime).

### Scope

This repository segment covers only the backend service (`backend-lumivia/`):

- REST API
- WebSocket broker
- Routing engine integration
- Flood risk module
- Persistence and runtime state
- Containerization and cloud deployment artifacts

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Built With

- [Java 17](https://openjdk.org/projects/jdk/17/)
- [Spring Boot 3.3](https://spring.io/projects/spring-boot)
- [Gradle 8](https://gradle.org/)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [PostgreSQL](https://www.postgresql.org/)
- [H2](https://www.h2database.com/html/main.html) (dev profile)
- [GraphHopper 9.1](https://github.com/graphhopper/graphhopper)
- [GeoTools 31](https://geotools.org/)
- [JTS](https://locationtech.github.io/jts/)
- [Docker](https://www.docker.com/)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Architecture

High-level flow:

1. Detection ingestion (`POST /api/vehiculos/deteccion`) stores historical data and updates active camera pool.
2. Emissions are aggregated into runtime camera state and published to `/topic/camaras`.
3. Routing (`POST /api/ruta`) computes fast and healthy alternatives with GraphHopper.
4. Healthy scoring combines:
   - Active emissions
   - Historical emissions (30-day lookback)
   - Flood penalties when `raining=true`
5. Flood risk (`/api/flood/*`) combines DEM elevation and user flood reports.

Design choices:

- Bounded geography for CDMX requests.
- Explicit DTO contracts for frontend interoperability.
- Runtime pools for low-latency updates, persistence for traceability.
- GeoJSON endpoints dedicated to map rendering pipelines.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Getting Started

### Prerequisites

- Java 17
- Git
- Docker (optional, for container runs)
- PostgreSQL (optional in dev, required for default profile)

### Installation

1. Clone the repository:

```bash
git clone https://github.com/FerVarg24/LumivIA.git
```

2. Enter backend directory:

```bash
cd LumivIA/backend-lumivia
```

3. Make sure Gradle wrapper is executable (Linux/macOS):

```bash
chmod +x gradlew
```

### Required data files

Routing and flood modules depend on large geospatial datasets that are intentionally excluded from git.

| Path | Purpose | Notes |
|------|---------|-------|
| `data/cdmx.osm.pbf` | OSM network for GraphHopper | Required for `/api/ruta` |
| `data/elevation/cdmx_dem.tif` | DEM for elevation-based flood risk | Required for elevation risk layer |
| `data/graph-cache/` | GraphHopper cache | Auto-generated on first import |

Download OSM data from:

- https://download.geofabrik.de/north-america/mexico.html

Important runtime notes:

- First GraphHopper import may take several minutes.
- `bootRun` is configured with `-Xmx4g` for routing workloads.

### Configuration

Main properties are defined in `src/main/resources/application.yml`.

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | HTTP server port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `lumivia` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `LUMIVIA_OSM_FILE` | `./data/cdmx.osm.pbf` | OSM file path |
| `LUMIVIA_GRAPH_CACHE` | `./data/graph-cache` | GraphHopper cache path |
| `LUMIVIA_FLOOD_DEM_FILE` | `./data/elevation/cdmx_dem.tif` | DEM file path |

`dev` profile uses H2 in-memory database via `application-dev.yml`.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Usage

### Run in development mode

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

H2 console (dev only):

- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:lumivia`
- User: `sa`

### Run with PostgreSQL

```bash
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=lumivia \
DB_USER=postgres \
DB_PASSWORD=postgres \
./gradlew bootRun
```

### Run with Docker

```bash
docker build -t lumivia-backend:latest .
docker run --rm -p 8080:8080 lumivia-backend:latest
```

### Run with Docker Compose

Development profile:

```bash
docker compose up backend
```

Production profile with PostgreSQL service:

```bash
docker compose --profile prod up backend-prod postgres
```

### Quick manual verification

- HTTP collection: `http/lumivia.http`
- Postman collection: `postman/LumivIA-Backend.postman_collection.json`
- WebSocket test page: `http://localhost:8080/ws-test.html`
- Flood test page: `http://localhost:8080/flood-test.html`
- Mapbox flood page: `http://localhost:8080/mapbox-flood.html`

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## API Overview

### Core endpoints

| Module | Method | Endpoint | Description |
|--------|--------|----------|-------------|
| Cameras | `GET` | `/api/camaras` | List fixed cameras |
| Vehicle | `POST` | `/api/vehiculos/deteccion` | Register detection and update camera state |
| History | `GET` | `/api/historial` | Query detections by camera and time range |
| Routing | `POST` | `/api/ruta` | Compute fast and healthy routes |
| Flood | `GET` | `/api/flood/risk` | Flood risk at a point |
| Flood | `POST` | `/api/flood/reports` | Create flood report |
| Flood | `GET` | `/api/flood/reports` | List active flood reports |
| Flood | `POST` | `/api/flood/reports/{id}/upvote` | Confirm an existing report |
| Flood GeoJSON | `GET` | `/api/flood/geojson/grid` | Heatmap-ready flood risk grid |
| Flood GeoJSON | `GET` | `/api/flood/geojson/reports` | Report points as GeoJSON |
| Flood GeoJSON | `GET` | `/api/flood/geojson/bounds` | Coverage polygon |

### Example requests

Create vehicle detection:

```bash
curl -X POST "http://localhost:8080/api/vehiculos/deteccion" \
  -H "Content-Type: application/json" \
  -d '{
    "camara": "camara_insurgentes_reforma",
    "timestamp": "2026-04-08T10:23:45Z",
    "tipo": "auto",
    "color": "#FF0000",
    "segundos_en_pantalla": 4.2
  }'
```

Calculate route:

```bash
curl -X POST "http://localhost:8080/api/ruta" \
  -H "Content-Type: application/json" \
  -d '{
    "origen": {"lat": 19.4326, "lng": -99.1332},
    "destino": {"lat": 19.4500, "lng": -99.1500},
    "perfil": "PEATON",
    "raining": true
  }'
```

Create flood report:

```bash
curl -X POST "http://localhost:8080/api/flood/reports" \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 19.4326,
    "lng": -99.1332,
    "severity": "MODERADO",
    "description": "Calle inundada"
  }'
```

### WebSocket contract

- SockJS/STOMP endpoint: `/ws`
- Topic: `/topic/camaras`
- Event model:
  - `vehiculo_nuevo` present when a new vehicle enters active pool.
  - `vehiculo_nuevo` null when TTL expiration updates camera aggregate only.

Vehicle types accepted by API:

- `auto`
- `moto`
- `camion`
- `bici`
- `peaton`

Important mapping detail:

- Route coordinates are returned as `[lng, lat]` for direct Mapbox compatibility.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Project Structure

```text
backend-lumivia/
├── src/main/java/com/lumivia/
│   ├── camera/
│   ├── vehicle/
│   ├── emissions/
│   ├── history/
│   ├── routing/
│   ├── flood/
│   ├── websocket/
│   └── common/
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── data.sql
│   └── static/
├── Dockerfile
├── docker-compose.yml
└── build.gradle
```

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Deployment Notes

The backend is containerized and ready for cloud deployment.

- Multi-stage Docker build (`Dockerfile`)
- Non-root runtime user in container
- Externalized runtime config through environment variables
- Verified deployment path on IBM Cloud Code Engine with IBM Container Registry

Container registry naming example:

```bash
us.icr.io/<namespace>/lumivia-backend:latest
```

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Open Source Vision

LumivIA Backend is designed to be useful beyond one deployment or one city pilot.

Core principles:

- Transparent contracts over hidden coupling.
- Reproducible local setup and cloud portability.
- Public-health-first routing criteria, not just shortest path optimization.
- Modular codebase ready for independent contributors.

What makes it valuable for open collaboration:

- Real urban-problem context (emissions + climate risk).
- Practical geospatial stack with production constraints.
- Clean separation between domain modules and transport layer.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a feature branch (`feature/your-change`).
3. Commit with clear intent and scope.
4. Open a pull request with context, impact, and test notes.

Recommended contribution areas:

- Automated testing coverage
- OpenAPI specification
- Auth and rate limiting
- Observability and metrics
- Data quality tooling for detections and reports

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Roadmap

- [ ] Add automated integration tests for routing and flood modules.
- [ ] Publish OpenAPI docs for all REST endpoints.
- [ ] Introduce authentication and service-to-service authorization.
- [ ] Add observability dashboards and alerting baselines.
- [ ] Publish benchmark profile for GraphHopper and flood calculations.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## License

No `LICENSE` file is currently included in this repository.

If you plan external contributions or public redistribution, add an explicit open source license (for example MIT, Apache-2.0, or GPL-3.0) before release.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Acknowledgments

- [GraphHopper](https://github.com/graphhopper/graphhopper) for routing engine capabilities.
- [Geofabrik](https://download.geofabrik.de/) for OpenStreetMap extracts.
- [NASA SRTM](https://www.earthdata.nasa.gov/) data used through DEM workflows.
- [GeoTools](https://geotools.org/) and [JTS](https://locationtech.github.io/jts/) for geospatial processing.
- [Best README Template](https://github.com/othneildrew/Best-README-Template) as structural inspiration.

<p align="right">(<a href="#readme-top">back to top</a>)</p>
