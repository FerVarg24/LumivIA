package com.lumivia.emissions;

import java.util.Arrays;

public enum VehicleType {
    AUTO("auto"),
    MOTO("moto"),
    CAMION("camion"),
    BICI("bici"),
    PEATON("peaton");

    private final String value;

    VehicleType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static VehicleType fromValue(String raw) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(raw))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de vehiculo no soportado: " + raw));
    }
}
