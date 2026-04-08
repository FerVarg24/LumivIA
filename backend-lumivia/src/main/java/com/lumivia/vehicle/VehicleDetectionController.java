package com.lumivia.vehicle;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehiculos")
public class VehicleDetectionController {

    private final VehicleDetectionService vehicleDetectionService;

    public VehicleDetectionController(VehicleDetectionService vehicleDetectionService) {
        this.vehicleDetectionService = vehicleDetectionService;
    }

    @PostMapping("/deteccion")
    @ResponseStatus(HttpStatus.CREATED)
    public CameraStateResponse registerDetection(@Valid @RequestBody VehicleDetectionRequest request) {
        return vehicleDetectionService.registerDetection(request);
    }
}
