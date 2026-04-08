package com.lumivia.flood;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FloodReportRepository extends JpaRepository<FloodReport, Long> {

    /**
     * Encuentra reportes activos (no expirados) dentro de un bounding box.
     */
    @Query("SELECT r FROM FloodReport r WHERE r.lat BETWEEN :minLat AND :maxLat " +
           "AND r.lng BETWEEN :minLng AND :maxLng AND r.expiresAt > :now ORDER BY r.timestamp DESC")
    List<FloodReport> findActiveInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("now") Instant now);

    /**
     * Encuentra reportes activos cercanos a un punto (para calcular riesgo).
     * Usa aproximacion de distancia con lat/lng (suficiente para CDMX).
     */
    @Query("SELECT r FROM FloodReport r WHERE r.expiresAt > :now " +
           "AND r.lat BETWEEN :lat - :radiusDeg AND :lat + :radiusDeg " +
           "AND r.lng BETWEEN :lng - :radiusDeg AND :lng + :radiusDeg")
    List<FloodReport> findActiveNearPoint(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusDeg") double radiusDeg,
            @Param("now") Instant now);

    /**
     * Encuentra todos los reportes activos.
     */
    @Query("SELECT r FROM FloodReport r WHERE r.expiresAt > :now ORDER BY r.timestamp DESC")
    List<FloodReport> findAllActive(@Param("now") Instant now);

    /**
     * Cuenta reportes activos en una zona.
     */
    @Query("SELECT COUNT(r) FROM FloodReport r WHERE r.expiresAt > :now " +
           "AND r.lat BETWEEN :minLat AND :maxLat AND r.lng BETWEEN :minLng AND :maxLng")
    long countActiveInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("now") Instant now);
}
