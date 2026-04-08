package com.lumivia.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record VehicleDetectionRequest(
        @NotBlank String camara,
        @NotNull Instant timestamp,
        @NotBlank String tipo,
        @NotBlank String color,
        @NotNull @Positive @JsonProperty("segundos_en_pantalla") Double segundosEnPantalla) {}
