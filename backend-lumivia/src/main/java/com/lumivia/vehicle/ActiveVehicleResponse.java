package com.lumivia.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActiveVehicleResponse(String id, String color, String tipo, @JsonProperty("ttl") double ttlSeconds) {}
