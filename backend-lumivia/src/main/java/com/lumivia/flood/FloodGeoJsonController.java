package com.lumivia.flood;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller that exposes flood data in GeoJSON format for Mapbox GL JS consumption.
 * 
 * Endpoints:
 * - GET /api/flood/geojson/reports - Flood reports as GeoJSON FeatureCollection
 * - GET /api/flood/geojson/grid    - Risk grid as GeoJSON for heatmap layer
 */
@RestController
@RequestMapping("/api/flood/geojson")
@CrossOrigin(origins = "*")
public class FloodGeoJsonController {

    private final FloodReportRepository reportRepository;
    private final FloodRiskService riskService;

    // Fixed grid covering CDMX - approximately 200 points
    // Bounds: lat 19.2 - 19.6, lng -99.35 - -98.95
    private static final double LAT_MIN = 19.20;
    private static final double LAT_MAX = 19.60;
    private static final double LNG_MIN = -99.35;
    private static final double LNG_MAX = -98.95;
    private static final int GRID_ROWS = 14;  // ~200 points (14x14=196)
    private static final int GRID_COLS = 14;

    public FloodGeoJsonController(FloodReportRepository reportRepository, FloodRiskService riskService) {
        this.reportRepository = reportRepository;
        this.riskService = riskService;
    }

    /**
     * Returns active flood reports as GeoJSON FeatureCollection.
     * Use with Mapbox circle or symbol layer.
     * 
     * Example Mapbox usage:
     * map.addSource('flood-reports', { type: 'geojson', data: '/api/flood/geojson/reports' });
     * map.addLayer({ id: 'reports', type: 'circle', source: 'flood-reports', ... });
     */
    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReportsGeoJson() {
        List<FloodReport> reports = reportRepository.findAllActive(java.time.Instant.now());
        
        List<Map<String, Object>> features = new ArrayList<>();
        for (FloodReport report : reports) {
            features.add(createReportFeature(report));
        }

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "FeatureCollection");
        geoJson.put("features", features);

        return ResponseEntity.ok(geoJson);
    }

    /**
     * Returns flood risk grid as GeoJSON FeatureCollection for heatmap visualization.
     * Grid covers CDMX with ~200 points. Risk is only calculated when raining=true.
     * 
     * @param raining Whether it's currently raining (activates flood risk calculation)
     * 
     * Example Mapbox usage:
     * map.addSource('flood-risk', { type: 'geojson', data: '/api/flood/geojson/grid?raining=true' });
     * map.addLayer({ id: 'heatmap', type: 'heatmap', source: 'flood-risk',
     *   paint: { 'heatmap-weight': ['get', 'risk'], ... }
     * });
     */
    @GetMapping("/grid")
    public ResponseEntity<Map<String, Object>> getRiskGrid(
            @RequestParam(defaultValue = "false") boolean raining) {
        
        List<Map<String, Object>> features = new ArrayList<>();
        
        double latStep = (LAT_MAX - LAT_MIN) / (GRID_ROWS - 1);
        double lngStep = (LNG_MAX - LNG_MIN) / (GRID_COLS - 1);

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                double lat = LAT_MIN + (row * latStep);
                double lng = LNG_MIN + (col * lngStep);
                
                double risk = raining ? riskService.calculateRisk(lat, lng) : 0.0;
                
                // Only include points with some risk for efficiency
                if (risk > 0.05 || !raining) {
                    features.add(createGridFeature(lat, lng, risk, raining));
                }
            }
        }

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "FeatureCollection");
        geoJson.put("metadata", createMetadata(raining, features.size()));
        geoJson.put("features", features);

        return ResponseEntity.ok(geoJson);
    }

    /**
     * Returns the grid bounds as GeoJSON Polygon.
     * Useful for showing the coverage area on the map.
     */
    @GetMapping("/bounds")
    public ResponseEntity<Map<String, Object>> getBounds() {
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "Feature");
        geoJson.put("properties", Map.of(
            "name", "CDMX Flood Risk Coverage",
            "gridRows", GRID_ROWS,
            "gridCols", GRID_COLS
        ));
        geoJson.put("geometry", Map.of(
            "type", "Polygon",
            "coordinates", List.of(List.of(
                List.of(LNG_MIN, LAT_MIN),
                List.of(LNG_MAX, LAT_MIN),
                List.of(LNG_MAX, LAT_MAX),
                List.of(LNG_MIN, LAT_MAX),
                List.of(LNG_MIN, LAT_MIN)
            ))
        ));

        return ResponseEntity.ok(geoJson);
    }

    // --- Helper methods ---

    private Map<String, Object> createReportFeature(FloodReport report) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", report.getId());
        
        // GeoJSON uses [lng, lat] order
        feature.put("geometry", Map.of(
            "type", "Point",
            "coordinates", List.of(report.getLng(), report.getLat())
        ));
        
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", report.getId());
        props.put("severity", report.getSeverity().name());
        props.put("severityValue", report.getSeverity().getWeight());
        props.put("description", report.getDescription());
        props.put("upvotes", report.getUpvotes());
        props.put("createdAt", report.getTimestamp().toString());
        props.put("expiresAt", report.getExpiresAt().toString());
        // For Mapbox styling
        props.put("color", getSeverityColor(report.getSeverity()));
        props.put("radius", getSeverityRadius(report.getSeverity()));
        feature.put("properties", props);
        
        return feature;
    }

    private Map<String, Object> createGridFeature(double lat, double lng, double risk, boolean raining) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        
        // GeoJSON uses [lng, lat] order
        feature.put("geometry", Map.of(
            "type", "Point",
            "coordinates", List.of(lng, lat)
        ));
        
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("risk", Math.round(risk * 100) / 100.0); // 2 decimal places
        props.put("riskLevel", getRiskLevel(risk));
        props.put("raining", raining);
        // Heatmap weight (0-1 scale, emphasized for visualization)
        props.put("weight", Math.min(1.0, risk * 1.5));
        feature.put("properties", props);
        
        return feature;
    }

    private Map<String, Object> createMetadata(boolean raining, int pointCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("raining", raining);
        metadata.put("pointCount", pointCount);
        metadata.put("gridRows", GRID_ROWS);
        metadata.put("gridCols", GRID_COLS);
        metadata.put("bounds", Map.of(
            "latMin", LAT_MIN,
            "latMax", LAT_MAX,
            "lngMin", LNG_MIN,
            "lngMax", LNG_MAX
        ));
        metadata.put("generatedAt", java.time.Instant.now().toString());
        return metadata;
    }

    private String getSeverityColor(FloodSeverity severity) {
        return switch (severity) {
            case LEVE -> "#FFC107";     // Yellow/amber
            case MODERADO -> "#FF9800"; // Orange
            case SEVERO -> "#F44336";   // Red
        };
    }

    private int getSeverityRadius(FloodSeverity severity) {
        return switch (severity) {
            case LEVE -> 8;
            case MODERADO -> 12;
            case SEVERO -> 16;
        };
    }

    private String getRiskLevel(double risk) {
        if (risk < 0.3) return "bajo";
        if (risk < 0.6) return "medio";
        return "alto";
    }
}
