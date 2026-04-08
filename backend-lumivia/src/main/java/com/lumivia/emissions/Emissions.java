package com.lumivia.emissions;

public record Emissions(double co2, double nox, double pm25) {
    public Emissions add(Emissions other) {
        return new Emissions(this.co2 + other.co2, this.nox + other.nox, this.pm25 + other.pm25);
    }

    public static Emissions zero() {
        return new Emissions(0.0, 0.0, 0.0);
    }
}
