package com.lumivia.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request para calcular rutas.
 *
 * @param origen Punto de origen
 * @param destino Punto de destino
 * @param perfil Perfil de routing (PEATON, CONDUCTOR, COMBINADA)
 * @param raining Si esta lloviendo, activa la logica de inundaciones (default: false)
 */
public record RouteRequest(
        @NotNull @Valid RoutePoint origen,
        @NotNull @Valid RoutePoint destino,
        @NotNull RouteProfile perfil,
        @JsonProperty("raining") Boolean raining) {

    /**
     * Constructor que permite omitir el campo raining (default false).
     */
    public RouteRequest {
        if (raining == null) {
            raining = false;
        }
    }

    public boolean isRaining() {
        return raining != null && raining;
    }

    public record RoutePoint(double lat, double lng) {}
}
