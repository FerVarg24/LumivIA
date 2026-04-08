package com.lumivia.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumivia.vehicle.VehicleDetection;
import java.time.Instant;

public record HistoryResponse(
        String camara,
        Instant timestamp,
        String tipo,
        String color,
        @JsonProperty("segundos_en_pantalla") double segundosEnPantalla,
        double co2,
        double nox,
        double pm25) {

    public static HistoryResponse from(VehicleDetection detection) {
        return new HistoryResponse(
                detection.getCamara(),
                detection.getTimestamp(),
                detection.getTipo(),
                detection.getColor(),
                detection.getSegundosEnPantalla(),
                detection.getCo2(),
                detection.getNox(),
                detection.getPm25());
    }
}
