package com.lumivia.camera;

import com.lumivia.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CameraService {

    private final CameraRepository cameraRepository;

    public CameraService(CameraRepository cameraRepository) {
        this.cameraRepository = cameraRepository;
    }

    public List<CameraResponse> getAll() {
        return cameraRepository.findAll().stream().map(CameraResponse::from).toList();
    }

    public Camera getByNombre(String nombre) {
        return cameraRepository.findByNombre(nombre)
                .orElseThrow(() -> new NotFoundException("Camara no encontrada: " + nombre));
    }
}
