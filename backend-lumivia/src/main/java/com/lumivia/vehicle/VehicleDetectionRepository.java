package com.lumivia.vehicle;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleDetectionRepository extends JpaRepository<VehicleDetection, Long> {
    List<VehicleDetection> findByCamaraAndTimestampBetweenOrderByTimestampAsc(
            String camara, Instant desde, Instant hasta);
}
