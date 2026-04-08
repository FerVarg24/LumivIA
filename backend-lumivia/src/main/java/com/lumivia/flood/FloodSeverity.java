package com.lumivia.flood;

/**
 * Severidad de inundacion reportada por usuarios.
 */
public enum FloodSeverity {
    LEVE(0.3),
    MODERADO(0.6),
    SEVERO(1.0);

    private final double weight;

    FloodSeverity(double weight) {
        this.weight = weight;
    }

    public double getWeight() {
        return weight;
    }
}
