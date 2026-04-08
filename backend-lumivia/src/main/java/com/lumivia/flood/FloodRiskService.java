package com.lumivia.flood;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Servicio principal de riesgo de inundacion.
 * Combina datos de elevacion (DEM) con reportes de usuarios (crowdsourcing).
 *
 * Formula: riesgo = (elevationWeight * elevationRisk) + (reportWeight * reportRisk)
 */
@Service
public class FloodRiskService {

    private static final Logger log = LoggerFactory.getLogger(FloodRiskService.class);

    // Radio de busqueda para reportes cercanos (en grados, ~500m)
    private static final double REPORT_SEARCH_RADIUS_DEG = 0.005;

    // Radio de influencia maximo de un reporte (en metros)
    private static final double REPORT_INFLUENCE_RADIUS_METERS = 300.0;

    private final ElevationService elevationService;
    private final FloodReportRepository reportRepository;

    private final double elevationWeight;
    private final double reportWeight;
    private final double riskThreshold;

    public FloodRiskService(
            ElevationService elevationService,
            FloodReportRepository reportRepository,
            @Value("${lumivia.flood.elevation-weight:0.4}") double elevationWeight,
            @Value("${lumivia.flood.report-weight:0.6}") double reportWeight,
            @Value("${lumivia.flood.risk-threshold:0.5}") double riskThreshold) {
        this.elevationService = elevationService;
        this.reportRepository = reportRepository;
        this.elevationWeight = elevationWeight;
        this.reportWeight = reportWeight;
        this.riskThreshold = riskThreshold;

        log.info("FloodRiskService inicializado: elevationWeight={}, reportWeight={}, threshold={}",
                elevationWeight, reportWeight, riskThreshold);
    }

    /**
     * Calcula el riesgo de inundacion en un punto.
     *
     * @return riesgo entre 0.0 (sin riesgo) y 1.0 (riesgo maximo)
     */
    public double calculateRisk(double lat, double lng) {
        double elevationRisk = calculateElevationRisk(lat, lng);
        double reportRisk = calculateReportRisk(lat, lng);

        // Combinar ambas fuentes
        double combinedRisk = (elevationWeight * elevationRisk) + (reportWeight * reportRisk);

        // Asegurar que este en rango [0, 1]
        return Math.min(1.0, Math.max(0.0, combinedRisk));
    }

    /**
     * Calcula el riesgo basado solo en elevacion.
     */
    public double calculateElevationRisk(double lat, double lng) {
        if (!elevationService.isEnabled()) {
            return 0.0;
        }
        return elevationService.getElevationRisk(lat, lng);
    }

    /**
     * Calcula el riesgo basado en reportes de usuarios cercanos.
     */
    public double calculateReportRisk(double lat, double lng) {
        Instant now = Instant.now();
        List<FloodReport> nearbyReports = reportRepository.findActiveNearPoint(
                lat, lng, REPORT_SEARCH_RADIUS_DEG, now);

        if (nearbyReports.isEmpty()) {
            return 0.0;
        }

        // Calcular riesgo ponderado por distancia y severidad
        double totalWeight = 0.0;
        double weightedRisk = 0.0;

        for (FloodReport report : nearbyReports) {
            double distanceMeters = haversineMeters(lat, lng, report.getLat(), report.getLng());

            if (distanceMeters > REPORT_INFLUENCE_RADIUS_METERS) {
                continue;
            }

            // Peso basado en distancia (mas cerca = mas peso)
            double distanceWeight = 1.0 - (distanceMeters / REPORT_INFLUENCE_RADIUS_METERS);

            // Ajustar por upvotes (mas confirmaciones = mas confiable)
            double upvoteBoost = 1.0 + (report.getUpvotes() * 0.1); // +10% por upvote

            // Riesgo del reporte segun severidad
            double reportSeverityRisk = report.getSeverity().getWeight();

            double weight = distanceWeight * upvoteBoost;
            totalWeight += weight;
            weightedRisk += weight * reportSeverityRisk;
        }

        if (totalWeight <= 0.0) {
            return 0.0;
        }

        return weightedRisk / totalWeight;
    }

    /**
     * Determina si el riesgo es suficientemente alto para evitar la zona.
     */
    public boolean shouldAvoid(double lat, double lng) {
        return calculateRisk(lat, lng) >= riskThreshold;
    }

    /**
     * Obtiene el umbral de riesgo configurado.
     */
    public double getRiskThreshold() {
        return riskThreshold;
    }

    /**
     * Calcula la penalizacion de ruta para un segmento basado en el riesgo.
     * Se usa en el routing para evitar zonas de alto riesgo.
     *
     * @return penalizacion (0 = sin penalizacion, valores altos = evitar)
     */
    public double calculateRoutePenalty(double lat, double lng, boolean isRaining) {
        if (!isRaining) {
            return 0.0; // Sin lluvia, sin penalizacion por inundacion
        }

        double risk = calculateRisk(lat, lng);

        if (risk < 0.3) {
            return 0.0;
        }
        if (risk < 0.5) {
            return 200.0; // Penalizacion moderada
        }
        if (risk < 0.7) {
            return 500.0; // Penalizacion alta
        }
        return 1000.0; // Evitar fuertemente
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6_371_000.0 * c;
    }
}
