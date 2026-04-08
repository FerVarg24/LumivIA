package com.lumivia.flood;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/flood")
public class FloodReportController {

    private static final double CDMX_MIN_LAT = 19.0;
    private static final double CDMX_MAX_LAT = 19.7;
    private static final double CDMX_MIN_LNG = -99.4;
    private static final double CDMX_MAX_LNG = -98.9;

    private static final int REPORT_EXPIRATION_HOURS = 6;

    private final FloodReportRepository repository;
    private final FloodRiskService floodRiskService;

    public FloodReportController(FloodReportRepository repository, FloodRiskService floodRiskService) {
        this.repository = repository;
        this.floodRiskService = floodRiskService;
    }

    /**
     * Crear un nuevo reporte de inundacion.
     */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public FloodReportResponse createReport(@Valid @RequestBody FloodReportRequest request) {
        validateBounds(request.lat(), request.lng());

        Instant now = Instant.now();
        Instant expiresAt = now.plus(REPORT_EXPIRATION_HOURS, ChronoUnit.HOURS);

        FloodReport report = new FloodReport(
                request.lat(),
                request.lng(),
                request.severity(),
                request.description(),
                now,
                expiresAt);

        FloodReport saved = repository.save(report);
        return FloodReportResponse.from(saved);
    }

    /**
     * Listar reportes activos, opcionalmente filtrados por bounding box.
     */
    @GetMapping("/reports")
    public List<FloodReportResponse> getReports(
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng) {

        Instant now = Instant.now();
        List<FloodReport> reports;

        if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
            reports = repository.findActiveInBoundingBox(minLat, maxLat, minLng, maxLng, now);
        } else {
            reports = repository.findAllActive(now);
        }

        return reports.stream().map(FloodReportResponse::from).toList();
    }

    /**
     * Consultar riesgo de inundacion en un punto especifico.
     */
    @GetMapping("/risk")
    public FloodRiskResponse getRisk(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "true") boolean raining) {

        validateBounds(lat, lng);

        if (!raining) {
            return new FloodRiskResponse(lat, lng, 0.0, "bajo", "No esta lloviendo - sin riesgo de inundacion activo");
        }

        double risk = floodRiskService.calculateRisk(lat, lng);
        String level = riskLevel(risk);
        String description = riskDescription(risk, level);

        return new FloodRiskResponse(lat, lng, risk, level, description);
    }

    /**
     * Upvote a un reporte existente (confirma que la inundacion sigue activa).
     */
    @PostMapping("/reports/{id}/upvote")
    public FloodReportResponse upvoteReport(@PathVariable Long id) {
        FloodReport report = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));

        if (report.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Reporte ya expiro");
        }

        report.incrementUpvotes();
        // Extender expiracion 1 hora por cada upvote (max 12h total)
        Instant newExpiry = report.getExpiresAt().plus(1, ChronoUnit.HOURS);
        Instant maxExpiry = report.getTimestamp().plus(12, ChronoUnit.HOURS);
        report.setExpiresAt(newExpiry.isBefore(maxExpiry) ? newExpiry : maxExpiry);

        return FloodReportResponse.from(repository.save(report));
    }

    private void validateBounds(double lat, double lng) {
        if (lat < CDMX_MIN_LAT || lat > CDMX_MAX_LAT || lng < CDMX_MIN_LNG || lng > CDMX_MAX_LNG) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Coordenadas fuera del bounding box de CDMX");
        }
    }

    private String riskLevel(double risk) {
        if (risk < 0.3) return "bajo";
        if (risk < 0.6) return "medio";
        return "alto";
    }

    private String riskDescription(double risk, String level) {
        return switch (level) {
            case "bajo" -> "Zona con bajo riesgo de inundacion";
            case "medio" -> "Zona con riesgo moderado - proceda con precaucion";
            case "alto" -> "Zona de alto riesgo - considere ruta alternativa";
            default -> "Riesgo desconocido";
        };
    }

    public record FloodRiskResponse(
            double lat,
            double lng,
            double risk,
            @com.fasterxml.jackson.annotation.JsonProperty("nivel_riesgo") String nivelRiesgo,
            String descripcion) {}
}
