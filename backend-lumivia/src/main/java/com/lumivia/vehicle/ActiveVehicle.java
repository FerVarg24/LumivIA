package com.lumivia.vehicle;

import com.lumivia.emissions.VehicleType;
import java.util.UUID;

public class ActiveVehicle {

    private final String id;
    private final String color;
    private final VehicleType tipo;
    private final double co2PerSecond;
    private final double noxPerSecond;
    private final double pm25PerSecond;
    private double ttl;

    public ActiveVehicle(
            String color, VehicleType tipo, double co2PerSecond, double noxPerSecond, double pm25PerSecond, double ttl) {
        this.id = UUID.randomUUID().toString();
        this.color = color;
        this.tipo = tipo;
        this.co2PerSecond = co2PerSecond;
        this.noxPerSecond = noxPerSecond;
        this.pm25PerSecond = pm25PerSecond;
        this.ttl = ttl;
    }

    public String getId() {
        return id;
    }

    public String getColor() {
        return color;
    }

    public VehicleType getTipo() {
        return tipo;
    }

    public double getCo2PerSecond() {
        return co2PerSecond;
    }

    public double getNoxPerSecond() {
        return noxPerSecond;
    }

    public double getPm25PerSecond() {
        return pm25PerSecond;
    }

    public double getTtl() {
        return ttl;
    }

    public void decrementTtl(double seconds) {
        this.ttl -= seconds;
    }
}
