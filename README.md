<a id="readme-top"></a>

<div align="center">
  <h1 align="center">LumivIA Backend</h1>
  <h1 align="center"><a href="https://github.com/CrXTrhx/LumivIA-F.git">Link a repositorio frontend</a></h1>
  <h1 align="center"><a href="https://github.com/FerVarg24/LumivIA-ComputerVision.git">Link a repositorio de computer vision</a></h1>
  <p align="center">
    Plataforma backend para movilidad urbana inteligente en CDMX,
    con emisiones en tiempo real, ruteo saludable e inteligencia de riesgo por inundacion.
  </p>

  <p align="center">
    <a href="./backend-lumivia"><strong>Ver codigo backend</strong></a>
    ·
    <a href="https://github.com/FerVarg24/LumivIA/issues">Reportar issue</a>
    ·
    <a href="https://github.com/FerVarg24/LumivIA/issues">Solicitar feature</a>
  </p>
</div>

<div align="center">

![Java](https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=007396)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white&labelColor=6DB33F)
![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=for-the-badge&logo=gradle&logoColor=white&labelColor=02303A)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql&logoColor=white&labelColor=336791)
![H2](https://img.shields.io/badge/H2-Dev_Profile-09476B?style=for-the-badge&labelColor=09476B)
![JPA](https://img.shields.io/badge/Spring_Data_JPA-ORM-6DB33F?style=for-the-badge&logo=spring&logoColor=white&labelColor=6DB33F)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP%20%2B%20SockJS-FF6B6B?style=for-the-badge&labelColor=FF6B6B)
![GraphHopper](https://img.shields.io/badge/GraphHopper-9.1-4CAF50?style=for-the-badge&labelColor=4CAF50)
![GeoTools](https://img.shields.io/badge/GeoTools-31.3-2E7D32?style=for-the-badge&labelColor=2E7D32)
![JTS](https://img.shields.io/badge/JTS-Geometry-455A64?style=for-the-badge&labelColor=455A64)
![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?style=for-the-badge&logo=docker&logoColor=white&labelColor=2496ED)
![IBM Cloud](https://img.shields.io/badge/IBM_Cloud-Code_Engine-1261FE?style=for-the-badge&logo=ibmcloud&logoColor=white&labelColor=1261FE)
![IBM ICR](https://img.shields.io/badge/IBM_Container_Registry-ICR-052FAD?style=for-the-badge&logo=ibm&logoColor=white&labelColor=052FAD)

</div>

---

## Tabla de contenido

- [Que es LumivIA Backend](#que-es-lumivia-backend)
- [Por que es innovador](#por-que-es-innovador)
- [Tecnologias usadas](#tecnologias-usadas)
- [Fuentes de datos geoespaciales](#fuentes-de-datos-geoespaciales)
- [Carpeta data (obligatoria)](#carpeta-data-obligatoria)
- [Arquitectura funcional](#arquitectura-funcional)
- [Endpoints principales](#endpoints-principales)
- [Como correr el backend](#como-correr-el-backend)
- [Docker y despliegue](#docker-y-despliegue)
- [Repositorio y alcance](#repositorio-y-alcance)
- [Contribuir](#contribuir)
- [Licencia](#licencia)

---

## Que es LumivIA Backend

LumivIA Backend es el motor de decision de la plataforma:

- Recibe detecciones de vehiculos en tiempo real.
- Calcula emisiones (CO2, NOx, PM2.5).
- Publica estado vivo de camaras por WebSocket.
- Calcula rutas rapidas vs rutas saludables.
- Estima riesgo de inundacion con relieve del terreno + reportes ciudadanos.
- Entrega salidas listas para mapas (GeoJSON y coordenadas `[lng, lat]`).

> El frontend y otros componentes van en repositorios separados.

---

## Por que es innovador

- Combina tres capas en una sola decision de movilidad:
  1. Emisiones activas (tiempo real)
  2. Emisiones historicas
  3. Riesgo por inundacion cuando llueve
- No usa una aproximacion simplista de "camara mas cercana"; aplica interpolacion espacial (IDW).
- Integra clima urbano y salud ambiental en el mismo flujo de ruteo.
- Está pensado para uso real: API, WebSocket, datos geoespaciales pesados y despliegue cloud.

---

## Tecnologias usadas

### Backend y API

- Java 17
- Spring Boot 3.3
- Spring Web
- Spring Validation
- Spring Data JPA
- Spring WebSocket (STOMP + SockJS)
- Gradle 8

### Datos y persistencia

- PostgreSQL (produccion)
- H2 (desarrollo)

### Geoespacial y ruteo

- GraphHopper 9.1 (motor de rutas)
- GeoTools 31.3
- JTS 1.20
- TwelveMonkeys ImageIO TIFF (lectura de GeoTIFF)

### Infraestructura

- Docker (multi-stage build)
- Docker Compose
- IBM Cloud Code Engine
- IBM Container Registry (ICR)

---

## Fuentes de datos geoespaciales

Estos datos son la base del analisis territorial:

- **Calles y red vial (OSM PBF)**
  - Fuente: Geofabrik (extractos OpenStreetMap)
  - URL: https://download.geofabrik.de/north-america/mexico.html
- **Altura del terreno / elevacion (DEM GeoTIFF)**
  - Dataset SRTM (NASA/DEM)
  - Usado para detectar zonas bajas con mayor probabilidad de inundacion

---

## Carpeta data (obligatoria)

La carpeta `backend-lumivia/data/` es clave para que el backend funcione completo.

Estructura esperada:

```text
backend-lumivia/data/
├── cdmx.osm.pbf
├── elevation/
│   └── cdmx_dem.tif
└── graph-cache/
```

### Que hace cada archivo

- `cdmx.osm.pbf`: red de calles para GraphHopper.
- `elevation/cdmx_dem.tif`: altura del terreno (modelo digital de elevacion).
- `graph-cache/`: cache que GraphHopper genera en el primer arranque.

### Importante

- Esta carpeta **no se sube a git** (archivos grandes).
- Sin `cdmx.osm.pbf`, el endpoint `/api/ruta` respondera `503`.
- El primer arranque con importacion de grafo puede tardar varios minutos.
- Se recomienda memoria alta para JVM (`-Xmx4g`).

---

## Arquitectura funcional

Flujo general:

1. `POST /api/vehiculos/deteccion` registra deteccion y emisiones.
2. Se actualiza estado activo de camara (pool con TTL).
3. Se emite evento por WebSocket a `/topic/camaras`.
4. `POST /api/ruta` calcula ruta rapida y saludable.
5. Si `raining=true`, se agrega penalizacion por riesgo de inundacion.
6. Endpoints flood entregan riesgo puntual, reportes y capas GeoJSON para mapa.

---

## Endpoints principales

### Trafico y emisiones

- `GET /api/camaras`
- `POST /api/vehiculos/deteccion`
- `GET /api/historial`

### Ruteo

- `POST /api/ruta`

### Inundaciones

- `GET /api/flood/risk`
- `POST /api/flood/reports`
- `GET /api/flood/reports`
- `POST /api/flood/reports/{id}/upvote`

### GeoJSON para mapas

- `GET /api/flood/geojson/grid`
- `GET /api/flood/geojson/reports`
- `GET /api/flood/geojson/bounds`

### WebSocket

- Endpoint: `/ws`
- Topic: `/topic/camaras`

---

## Como correr el backend

### Desarrollo (H2)

```bash
cd backend-lumivia
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

### Produccion local (PostgreSQL)

```bash
cd backend-lumivia
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=lumivia \
DB_USER=postgres \
DB_PASSWORD=postgres \
./gradlew bootRun
```

### Pruebas manuales utiles

- `backend-lumivia/http/lumivia.http`
- `backend-lumivia/postman/LumivIA-Backend.postman_collection.json`
- `http://localhost:8080/ws-test.html`
- `http://localhost:8080/flood-test.html`
- `http://localhost:8080/mapbox-flood.html`

---

## Docker y despliegue

### Build local

```bash
cd backend-lumivia
docker build -t lumivia-backend:latest .
```

### Run local

```bash
docker run --rm -p 8080:8080 lumivia-backend:latest
```

### Docker Compose

```bash
cd backend-lumivia
docker compose up backend
```

### Nube (IBM Cloud)

- Imagen preparada para IBM Container Registry.
- Despliegue validado en IBM Code Engine.
- Ejemplo de tag:

```bash
us.icr.io/<namespace>/lumivia-backend:latest
```

---

## Repositorio y alcance

Este README principal esta orientado al backend para que en GitHub se vea claramente el valor tecnico del proyecto.

Documentacion detallada del backend:

- `backend-lumivia/README.md`

---

## Contribuir

Si quieres contribuir:

1. Haz fork del repositorio.
2. Crea una rama (`feature/mi-mejora`).
3. Realiza cambios con contexto tecnico claro.
4. Abre Pull Request con descripcion y forma de validacion.

---

## Licencia

Actualmente no existe un archivo `LICENSE` en el repositorio.

Para apertura formal del proyecto, se recomienda agregar una licencia explicita (MIT, Apache-2.0, GPL-3.0, etc.).

<p align="right">(<a href="#readme-top">Volver arriba</a>)</p>
