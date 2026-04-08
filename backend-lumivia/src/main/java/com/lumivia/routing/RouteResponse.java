package com.lumivia.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumivia.emissions.Emissions;
import java.util.List;

public record RouteResponse(
        @JsonProperty("ruta_rapida") RouteOption rutaRapida,
        @JsonProperty("ruta_saludable") RouteOption rutaSaludable,
        @JsonProperty("ahorro_co2") double ahorroCo2,
        @JsonProperty("tiempo_extra_min") int tiempoExtraMin,
        @JsonProperty("raining") boolean raining) {

    public record RouteOption(
            @JsonProperty("distancia_km") double distanciaKm,
            @JsonProperty("tiempo_estimado_min") int tiempoEstimadoMin,
            @JsonProperty("nivel_riesgo") String nivelRiesgo,
            List<List<Double>> coordenadas,
            @JsonProperty("emisiones_ruta") Emissions emisionesRuta,
            String descripcion) {}
}
