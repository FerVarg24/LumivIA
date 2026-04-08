package com.lumivia.flood;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request para crear un reporte de inundacion.
 */
public record FloodReportRequest(
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull FloodSeverity severity,
        @Size(max = 500) String description) {}
