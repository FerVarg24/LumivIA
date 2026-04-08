package com.lumivia.flood;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Response de un reporte de inundacion.
 */
public record FloodReportResponse(
        Long id,
        double lat,
        double lng,
        FloodSeverity severity,
        String description,
        Instant timestamp,
        @JsonProperty("expires_at") Instant expiresAt,
        int upvotes) {

    public static FloodReportResponse from(FloodReport report) {
        return new FloodReportResponse(
                report.getId(),
                report.getLat(),
                report.getLng(),
                report.getSeverity(),
                report.getDescription(),
                report.getTimestamp(),
                report.getExpiresAt(),
                report.getUpvotes());
    }
}
