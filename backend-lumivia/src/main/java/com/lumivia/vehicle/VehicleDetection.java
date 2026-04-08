package com.lumivia.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "vehicle_detections")
public class VehicleDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camara", nullable = false)
    private String camara;

    @Column(name = "detected_at", nullable = false)
    private Instant timestamp;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "color", nullable = false)
    private String color;

    @Column(name = "segundos_en_pantalla", nullable = false)
    private double segundosEnPantalla;

    @Column(name = "co2", nullable = false)
    private double co2;

    @Column(name = "nox", nullable = false)
    private double nox;

    @Column(name = "pm25", nullable = false)
    private double pm25;

    public Long getId() {
        return id;
    }

    public String getCamara() {
        return camara;
    }

    public void setCamara(String camara) {
        this.camara = camara;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getSegundosEnPantalla() {
        return segundosEnPantalla;
    }

    public void setSegundosEnPantalla(double segundosEnPantalla) {
        this.segundosEnPantalla = segundosEnPantalla;
    }

    public double getCo2() {
        return co2;
    }

    public void setCo2(double co2) {
        this.co2 = co2;
    }

    public double getNox() {
        return nox;
    }

    public void setNox(double nox) {
        this.nox = nox;
    }

    public double getPm25() {
        return pm25;
    }

    public void setPm25(double pm25) {
        this.pm25 = pm25;
    }
}
