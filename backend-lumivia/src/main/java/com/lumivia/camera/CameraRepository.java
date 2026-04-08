package com.lumivia.camera;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    Optional<Camera> findByNombre(String nombre);
}
