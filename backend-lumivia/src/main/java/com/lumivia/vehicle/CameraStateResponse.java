package com.lumivia.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumivia.emissions.Emissions;

public record CameraStateResponse(
        String camara,
        double lat,
        double lng,
        @JsonProperty("vehiculo_nuevo") ActiveVehicleResponse vehiculoNuevo,
        @JsonProperty("opacidad_humo") double opacidadHumo,
        @JsonProperty("color_humo") String colorHumo,
        Emissions emisiones) {}
