package com.lumivia.flood;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Reporte de inundacion enviado por usuarios (crowdsourcing).
 */
@Entity
@Table(name = "flood_reports", indexes = {
    @Index(name = "idx_flood_lat_lng", columnList = "lat, lng"),
    @Index(name = "idx_flood_timestamp", columnList = "timestamp")
})
public class FloodReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FloodSeverity severity;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "upvotes", nullable = false)
    private int upvotes = 0;

    public FloodReport() {}

    public FloodReport(double lat, double lng, FloodSeverity severity, String description, Instant timestamp, Instant expiresAt) {
        this.lat = lat;
        this.lng = lng;
        this.severity = severity;
        this.description = description;
        this.timestamp = timestamp;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public FloodSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(FloodSeverity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getUpvotes() {
        return upvotes;
    }

    public void setUpvotes(int upvotes) {
        this.upvotes = upvotes;
    }

    public void incrementUpvotes() {
        this.upvotes++;
    }
}
