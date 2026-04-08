package com.lumivia.emissions;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EmissionsService {

    private final Map<VehicleType, EmissionFactors> factorsByType = Map.of(
            VehicleType.AUTO, new EmissionFactors(30.0, 0.08, 0.002),
            VehicleType.MOTO, new EmissionFactors(18.0, 0.12, 0.004),
            VehicleType.CAMION, new EmissionFactors(80.0, 0.40, 0.012),
            VehicleType.BICI, new EmissionFactors(0.0, 0.0, 0.0),
            VehicleType.PEATON, new EmissionFactors(0.0, 0.0, 0.0));

    public Emissions calculate(VehicleType vehicleType, double secondsInScene) {
        EmissionFactors factors = factorsByType.get(vehicleType);
        if (factors == null) {
            throw new IllegalArgumentException("No hay factores para tipo de vehiculo: " + vehicleType);
        }
        return new Emissions(
                factors.co2PerSecond() * secondsInScene,
                factors.noxPerSecond() * secondsInScene,
                factors.pm25PerSecond() * secondsInScene);
    }
}
