package com.lumivia.history;

import com.lumivia.camera.CameraService;
import com.lumivia.vehicle.VehicleDetectionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {

    private final CameraService cameraService;
    private final VehicleDetectionRepository vehicleDetectionRepository;

    public HistoryService(CameraService cameraService, VehicleDetectionRepository vehicleDetectionRepository) {
        this.cameraService = cameraService;
        this.vehicleDetectionRepository = vehicleDetectionRepository;
    }

    public List<HistoryResponse> getHistory(String camara, Instant desde, Instant hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("El parametro 'desde' no puede ser mayor a 'hasta'");
        }

        cameraService.getByNombre(camara);

        return vehicleDetectionRepository
                .findByCamaraAndTimestampBetweenOrderByTimestampAsc(camara, desde, hasta)
                .stream()
                .map(HistoryResponse::from)
                .toList();
    }
}
