# LumivIA Backend

Backend de LumivIA construido con Spring Boot 3, Java 17 y Gradle (Groovy DSL).

Este README esta pensado para que frontend pueda integrar todo el flujo solo con este documento.

## Modulos activos

- `camera/`: camaras fijas (`GET /api/camaras`)
- `vehicle/`: detecciones YOLO, pool activo con TTL, estado en tiempo real por WebSocket
- `emissions/`: calculo de CO2, NOx, PM2.5 por tipo de vehiculo
- `history/`: historico por camara y rango de fechas (`GET /api/historial`)
- `routing/`: rutas rapida vs saludable con GraphHopper (`POST /api/ruta`)
- `websocket/`: STOMP/SockJS (`/ws`, `/topic/camaras`)
- `flood/`: prediccion de inundaciones con DEM + crowdsourcing (`GET /api/flood/*`)

## Requisitos

- Java 17
- PostgreSQL (default) o H2 (perfil `dev`)

## Ejecutar backend

### PowerShell (Windows, recomendado)

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

### Bash (Linux/macOS)

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

### PostgreSQL (perfil default)

Variables sugeridas:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=lumivia
export DB_USER=postgres
export DB_PASSWORD=postgres
./gradlew bootRun
```

H2 Console en `dev`:

- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:lumivia`
- User: `sa`

## Contratos para frontend

### 1) GET `/api/camaras`

Devuelve camaras fijas para pintar en mapa.

```json
[
  {
    "nombre": "camara_insurgentes_reforma",
    "lat": 19.4326,
    "lng": -99.1332,
    "descripcion": "Insurgentes y Reforma"
  }
]
```

### 2) POST `/api/vehiculos/deteccion`

Entrada:

```json
{
  "camara": "camara_insurgentes_reforma",
  "timestamp": "2026-04-07T10:23:45Z",
  "tipo": "auto",
  "color": "#FF0000",
  "segundos_en_pantalla": 4.2
}
```

`tipo` valido: `auto`, `moto`, `camion`, `bici`, `peaton`.

### 3) WebSocket STOMP `/topic/camaras`

- Endpoint SockJS/STOMP: `/ws`
- Topic: `/topic/camaras`

El backend emite en dos casos:

1. Entra vehiculo nuevo (`vehiculo_nuevo` con datos)
2. Expira vehiculo en scheduler (`vehiculo_nuevo: null`)

Payload con vehiculo nuevo:

```json
{
  "camara": "camara_insurgentes_reforma",
  "lat": 19.4326,
  "lng": -99.1332,
  "vehiculo_nuevo": {
    "id": "a3f1c2d4-...",
    "color": "#FF0000",
    "tipo": "auto",
    "ttl": 4.2
  },
  "opacidad_humo": 0.73,
  "color_humo": "#000000",
  "emisiones": {
    "co2": 364.8,
    "nox": 1.44,
    "pm25": 0.04
  }
}
```

Payload por expiracion:

```json
{
  "camara": "camara_insurgentes_reforma",
  "lat": 19.4326,
  "lng": -99.1332,
  "vehiculo_nuevo": null,
  "opacidad_humo": 0.45,
  "color_humo": "#000000",
  "emisiones": {
    "co2": 225.0,
    "nox": 0.89,
    "pm25": 0.03
  }
}
```

Notas frontend:

- `vehiculo_nuevo.id` es UUID unico por vehiculo activo.
- Usa `vehiculo_nuevo.ttl` para animar/remover localmente en mapa.
- Cuando `vehiculo_nuevo` es `null`, solo actualiza humo/opacidad/emisiones.

### 4) GET `/api/historial`

```bash
curl "http://localhost:8080/api/historial?camara=camara_insurgentes_reforma&desde=2026-04-07T00:00:00Z&hasta=2026-04-07T23:59:59Z"
```

Respuesta: lista de detecciones historicas con emisiones calculadas.

### 5) POST `/api/ruta`

Request (no cambia):

```json
{
  "origen": {"lat": 19.4326, "lng": -99.1332},
  "destino": {"lat": 19.4500, "lng": -99.1500},
  "perfil": "PEATON"
}
```

Response (siempre dos rutas):

```json
{
  "ruta_rapida": {
    "distancia_km": 0.33,
    "tiempo_estimado_min": 4,
    "nivel_riesgo": "alto",
    "coordenadas": [[-99.1332, 19.4326], [-99.1340, 19.4331]],
    "emisiones_ruta": {
      "co2": 17700.0,
      "nox": 47.2,
      "pm25": 1.18
    },
    "descripcion": "Ruta mas corta con carga alta: CO2 actual 840.0 g y CO2 historico 320.0 g. Foco dominante: camara_insurgentes_reforma (920.0 g CO2 estimados)."
  },
  "ruta_saludable": {
    "distancia_km": 0.51,
    "tiempo_estimado_min": 6,
    "nivel_riesgo": "bajo",
    "coordenadas": [[-99.1332, 19.4326], [-99.1355, 19.4338]],
    "emisiones_ruta": {
      "co2": 430.0,
      "nox": 1.6,
      "pm25": 0.06
    },
    "descripcion": "Ruta saludable recomendada: reduce 1270.0 g CO2 (74.7%), evita carga actual e historica, +2 min. Se evita el foco principal de camara_insurgentes_reforma."
  },
  "ahorro_co2": 1270.0,
  "tiempo_extra_min": 2
}
```

Notas frontend claves:

- `coordenadas` estan en formato `[lng, lat]` (Mapbox).
- `nivel_riesgo` se calcula por CO2 total de cada ruta:
  - `bajo` < 200 g
  - `medio` < 500 g
  - `alto` >= 500 g
- `ahorro_co2` y `tiempo_extra_min` son las diferencias entre rapida y saludable.
- El backend nunca recomienda una ruta "saludable" peor en CO2 que la rapida.
- Si las alternativas salen peores o iguales, ambas rutas pueden coincidir y `ahorro_co2` sera `0.0`.

## Como decide el motor de routing

Resumen de decision actual:

1. Calcula `ruta_rapida` con GraphHopper.
2. Estima exposicion en la ruta usando dos capas:
   - emisiones activas (tiempo real)
   - emisiones historicas (promedio ultimos 30 dias por camara)
3. Interpola espacialmente con IDW (no solo camara mas cercana):
   - 4 camaras vecinas
   - potencia 2.0
   - radio de influencia por camara: 50 m
4. Solo busca/forza alternativa saludable si detecta carga alta:
   - CO2 actual >= 200 g o CO2 historico >= 200 g
5. Entre alternativas, escoge la de menor puntaje saludable:
   - `peso = distancia + (co2 * 5000)` para `PEATON`/`COMBINADA`
6. Si no hay alternativa real, devuelve nota:
   - `No hay ruta alternativa disponible en esta zona`

## Perfiles de ruta

- `PEATON`: prioriza menor exposicion a emisiones.
- `CONDUCTOR`: hoy queda orientado a distancia/flood (flood todavia pendiente).
- `COMBINADA`: balancea emisiones y flood (flood pendiente).

## Configuracion OSM/GraphHopper

En `application.yml`:

```yaml
lumivia:
  osm-file: ./data/cdmx.osm.pbf
  graph-cache: ./data/graph-cache
```

Descarga OSM (Geofabrik):

- https://download.geofabrik.de/north-america/mexico.html

Coloca el archivo en `./data/cdmx.osm.pbf`.

Si falta el archivo, `POST /api/ruta` responde `503`.

Importante en primer arranque:

- El primer `importOrLoad` puede tardar varios minutos.
- Hasta que termine, el modulo puede no estar disponible.

## Datos de prueba

- Seed automatico: `src/main/resources/data.sql`
- Pruebas rapidas:
  - `http/lumivia.http`
  - `postman/LumivIA-Backend.postman_collection.json`
  - `http://localhost:8080/ws-test.html`

## Checklist rapido para frontend

1. Cargar camaras (`GET /api/camaras`) y pintarlas en mapa.
2. Suscribirse a `/topic/camaras` por STOMP.
3. En evento con `vehiculo_nuevo`, agregar marker temporal por `id` y TTL.
4. En evento con `vehiculo_nuevo: null`, solo refrescar humo/opacidad/emisiones.
5. Para rutas, pedir `POST /api/ruta` y pintar dos lineas:
   - rapida (estilo base)
   - saludable (estilo destacado)
6. Mostrar CTA con `ahorro_co2` y `tiempo_extra_min`.

---

## Modulo de Inundaciones (flood/)

Sistema de prediccion de riesgo de inundacion que combina:
- **Datos de elevacion (DEM)**: SRTM 30m de NASA para identificar zonas bajas
- **Reportes de usuarios (crowdsourcing)**: Puntos reportados por la comunidad

### Comportamiento clave

- El sistema de inundaciones **SOLO se activa cuando `raining=true`**
- Si no esta lloviendo, el riesgo de inundacion es 0 en todos los puntos
- Formula de riesgo: `riesgo = (0.4 * elevationRisk) + (0.6 * reportRisk)`

---

## Endpoints de Inundaciones

### 6) GET `/api/flood/risk`

Consulta el riesgo de inundacion en un punto especifico.

```bash
curl "http://localhost:8080/api/flood/risk?lat=19.4326&lng=-99.1332&raining=true"
```

Respuesta:

```json
{
  "lat": 19.4326,
  "lng": -99.1332,
  "risk": 0.42,
  "descripcion": "Zona con riesgo moderado - proceda con precaucion",
  "nivel_riesgo": "medio"
}
```

| Parametro | Tipo | Requerido | Default | Descripcion |
|-----------|------|-----------|---------|-------------|
| `lat` | double | Si | - | Latitud (19.0-19.7) |
| `lng` | double | Si | - | Longitud (-99.4 a -98.9) |
| `raining` | boolean | No | false | Si esta lloviendo |

### 7) POST `/api/flood/reports`

Crear un reporte de inundacion (crowdsourcing anonimo).

```bash
curl -X POST "http://localhost:8080/api/flood/reports" \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 19.4326,
    "lng": -99.1332,
    "severity": "MODERADO",
    "description": "Calle inundada, agua hasta la rodilla"
  }'
```

Valores de `severity`:
- `LEVE` - Encharcamiento menor (peso 0.3)
- `MODERADO` - Calle inundada (peso 0.6)
- `SEVERO` - Inundacion peligrosa (peso 1.0)

Respuesta:

```json
{
  "id": 1,
  "lat": 19.4326,
  "lng": -99.1332,
  "severity": "MODERADO",
  "description": "Calle inundada, agua hasta la rodilla",
  "timestamp": "2026-04-07T22:30:00Z",
  "expiresAt": "2026-04-08T04:30:00Z",
  "upvotes": 0
}
```

### 8) GET `/api/flood/reports`

Obtener reportes activos (no expirados).

```bash
# Todos los reportes activos
curl "http://localhost:8080/api/flood/reports"

# Filtrar por bounding box
curl "http://localhost:8080/api/flood/reports?minLat=19.4&maxLat=19.5&minLng=-99.2&maxLng=-99.1"
```

### 9) POST `/api/flood/reports/{id}/upvote`

Confirmar/validar un reporte existente (incrementa confiabilidad).

```bash
curl -X POST "http://localhost:8080/api/flood/reports/1/upvote"
```

---

## Endpoints GeoJSON para Mapbox

Estos endpoints devuelven datos en formato GeoJSON listo para consumir en Mapbox GL JS.

### 10) GET `/api/flood/geojson/grid`

Devuelve un grid de ~200 puntos cubriendo CDMX con el riesgo de inundacion.
**Ideal para renderizar como heatmap.**

```bash
# Con lluvia (calcula riesgo real)
curl "http://localhost:8080/api/flood/geojson/grid?raining=true"

# Sin lluvia (todos los riesgos son 0)
curl "http://localhost:8080/api/flood/geojson/grid?raining=false"
```

Respuesta:

```json
{
  "type": "FeatureCollection",
  "metadata": {
    "raining": true,
    "pointCount": 136,
    "gridRows": 14,
    "gridCols": 14,
    "bounds": {
      "latMin": 19.2,
      "latMax": 19.6,
      "lngMin": -99.35,
      "lngMax": -98.95
    },
    "generatedAt": "2026-04-07T22:30:00Z"
  },
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Point",
        "coordinates": [-99.1332, 19.4326]
      },
      "properties": {
        "risk": 0.42,
        "riskLevel": "medio",
        "raining": true,
        "weight": 0.63
      }
    }
  ]
}
```

### 11) GET `/api/flood/geojson/reports`

Devuelve los reportes de usuarios como GeoJSON FeatureCollection.
**Ideal para renderizar como circulos/markers.**

```bash
curl "http://localhost:8080/api/flood/geojson/reports"
```

Respuesta:

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": 1,
      "geometry": {
        "type": "Point",
        "coordinates": [-99.1332, 19.4326]
      },
      "properties": {
        "id": 1,
        "severity": "MODERADO",
        "severityValue": 0.6,
        "description": "Calle inundada",
        "upvotes": 3,
        "createdAt": "2026-04-07T22:30:00Z",
        "expiresAt": "2026-04-08T04:30:00Z",
        "color": "#FF9800",
        "radius": 12
      }
    }
  ]
}
```

### 12) GET `/api/flood/geojson/bounds`

Devuelve el poligono de cobertura del sistema de inundaciones.

```bash
curl "http://localhost:8080/api/flood/geojson/bounds"
```

---

## Integracion Mapbox GL JS - Codigo Completo

### Paso 1: Agregar sources

```javascript
map.on('load', () => {
  // Source para el heatmap de riesgo
  map.addSource('flood-risk', {
    type: 'geojson',
    data: 'http://localhost:8080/api/flood/geojson/grid?raining=false'
  });

  // Source para los reportes de usuarios
  map.addSource('flood-reports', {
    type: 'geojson',
    data: 'http://localhost:8080/api/flood/geojson/reports'
  });
});
```

### Paso 2: Agregar heatmap layer (riesgo de inundacion)

```javascript
map.addLayer({
  id: 'flood-heatmap',
  type: 'heatmap',
  source: 'flood-risk',
  paint: {
    // Peso basado en el valor de riesgo
    'heatmap-weight': [
      'interpolate', ['linear'],
      ['get', 'risk'],
      0, 0,
      0.3, 0.3,
      0.6, 0.6,
      1, 1
    ],
    // Intensidad aumenta con zoom
    'heatmap-intensity': [
      'interpolate', ['linear'],
      ['zoom'],
      9, 0.5,
      12, 1,
      15, 1.5
    ],
    // Rampa de colores: verde (seguro) -> rojo (peligro)
    'heatmap-color': [
      'interpolate', ['linear'],
      ['heatmap-density'],
      0, 'rgba(0, 255, 0, 0)',
      0.2, 'rgba(0, 255, 0, 0.4)',
      0.4, 'rgba(255, 255, 0, 0.6)',
      0.6, 'rgba(255, 165, 0, 0.7)',
      0.8, 'rgba(255, 69, 0, 0.8)',
      1, 'rgba(255, 0, 0, 0.9)'
    ],
    // Radio aumenta con zoom
    'heatmap-radius': [
      'interpolate', ['linear'],
      ['zoom'],
      9, 20,
      12, 30,
      15, 50
    ],
    'heatmap-opacity': 0.8
  }
});
```

### Paso 3: Agregar layer de reportes (circulos)

```javascript
map.addLayer({
  id: 'flood-reports-circle',
  type: 'circle',
  source: 'flood-reports',
  paint: {
    // Usa las propiedades del GeoJSON directamente
    'circle-radius': ['get', 'radius'],
    'circle-color': ['get', 'color'],
    'circle-stroke-width': 2,
    'circle-stroke-color': '#ffffff',
    'circle-opacity': 0.8
  }
});
```

### Paso 4: Actualizar cuando cambie el estado de lluvia

```javascript
let isRaining = false;

function toggleRain(raining) {
  isRaining = raining;
  
  // Recargar el grid con el nuevo estado
  fetch(`http://localhost:8080/api/flood/geojson/grid?raining=${isRaining}`)
    .then(res => res.json())
    .then(data => {
      map.getSource('flood-risk').setData(data);
    });
}

// Ejemplo: activar modo lluvia
toggleRain(true);
```

### Paso 5: Popup al hacer clic en un reporte

```javascript
map.on('click', 'flood-reports-circle', (e) => {
  const props = e.features[0].properties;
  const coords = e.features[0].geometry.coordinates;
  
  new mapboxgl.Popup()
    .setLngLat(coords)
    .setHTML(`
      <strong>Reporte de Inundacion</strong><br>
      Severidad: ${props.severity}<br>
      ${props.description || 'Sin descripcion'}<br>
      <small>${props.upvotes} confirmaciones</small>
    `)
    .addTo(map);
});

// Cambiar cursor al pasar sobre reportes
map.on('mouseenter', 'flood-reports-circle', () => {
  map.getCanvas().style.cursor = 'pointer';
});
map.on('mouseleave', 'flood-reports-circle', () => {
  map.getCanvas().style.cursor = '';
});
```

### Paso 6: Crear nuevo reporte desde el mapa

```javascript
let reportMode = false;

function enableReportMode() {
  reportMode = true;
  map.getCanvas().style.cursor = 'crosshair';
}

map.on('click', (e) => {
  if (!reportMode) return;
  
  const report = {
    lat: e.lngLat.lat,
    lng: e.lngLat.lng,
    severity: 'MODERADO', // O mostrar selector al usuario
    description: prompt('Descripcion (opcional):')
  };
  
  fetch('http://localhost:8080/api/flood/reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(report)
  })
  .then(res => res.json())
  .then(() => {
    // Recargar reportes
    fetch('http://localhost:8080/api/flood/geojson/reports')
      .then(res => res.json())
      .then(data => map.getSource('flood-reports').setData(data));
    
    reportMode = false;
    map.getCanvas().style.cursor = '';
  });
});
```

---

## Integracion con Routing (raining parameter)

El endpoint de rutas ahora acepta `raining` para considerar zonas de inundacion:

### POST `/api/ruta` con lluvia

```json
{
  "origen": {"lat": 19.4326, "lng": -99.1332},
  "destino": {"lat": 19.4500, "lng": -99.1500},
  "perfil": "CONDUCTOR",
  "raining": true
}
```

Cuando `raining: true`:
- La ruta saludable evita zonas con riesgo de inundacion > 0.5
- La respuesta incluye `"raining": true` para confirmar

```json
{
  "ruta_rapida": { ... },
  "ruta_saludable": { ... },
  "ahorro_co2": 450.0,
  "tiempo_extra_min": 3,
  "raining": true
}
```

---

## Colores y estilos recomendados

### Severidad de reportes

| Severidad | Color | Radio | Descripcion |
|-----------|-------|-------|-------------|
| LEVE | `#FFC107` (amarillo) | 8px | Encharcamiento |
| MODERADO | `#FF9800` (naranja) | 12px | Calle inundada |
| SEVERO | `#F44336` (rojo) | 16px | Peligroso |

### Niveles de riesgo en heatmap

| Riesgo | Color | Descripcion |
|--------|-------|-------------|
| 0.0 - 0.3 | Verde | Bajo riesgo |
| 0.3 - 0.6 | Amarillo | Riesgo medio |
| 0.6 - 0.8 | Naranja | Riesgo alto |
| 0.8 - 1.0 | Rojo | Riesgo severo |

---

## Pagina de pruebas

Disponible en: `http://localhost:8080/mapbox-flood.html`

(Requiere reemplazar `YOUR_MAPBOX_TOKEN_HERE` con tu token real de Mapbox)

---

## Checklist de integracion inundaciones

1. [ ] Agregar toggle "Esta lloviendo" en UI
2. [ ] Agregar source `flood-risk` con endpoint `/api/flood/geojson/grid`
3. [ ] Agregar layer `heatmap` para visualizar riesgo
4. [ ] Agregar source `flood-reports` con endpoint `/api/flood/geojson/reports`
5. [ ] Agregar layer `circle` para mostrar reportes
6. [ ] Implementar click en reportes para mostrar popup
7. [ ] Implementar modo "reportar inundacion" con POST
8. [ ] Pasar `raining: true/false` en requests de `/api/ruta`
9. [ ] Actualizar datos cuando cambie estado de lluvia
