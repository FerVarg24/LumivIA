package com.lumivia.vehicle;

import com.lumivia.camera.Camera;
import com.lumivia.camera.CameraResponse;
import com.lumivia.camera.CameraService;
import com.lumivia.emissions.Emissions;
import com.lumivia.emissions.EmissionsService;
import com.lumivia.emissions.VehicleType;
import com.lumivia.websocket.CameraStatePublisher;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleDetectionService {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6})$");
    private static final String SMOKE_COLOR = "#000000";
    private static final double OPACITY_CO2_SCALE = 500.0;

    private final Map<String, CopyOnWriteArrayList<ActiveVehicle>> activePoolByCamera = new ConcurrentHashMap<>();
    private final Map<String, CameraRuntimeState> cameraRuntimeStateByName = new ConcurrentHashMap<>();

    private final CameraService cameraService;
    private final EmissionsService emissionsService;
    private final VehicleDetectionRepository vehicleDetectionRepository;
    private final CameraStatePublisher cameraStatePublisher;

    public VehicleDetectionService(
            CameraService cameraService,
            EmissionsService emissionsService,
            VehicleDetectionRepository vehicleDetectionRepository,
            CameraStatePublisher cameraStatePublisher) {
        this.cameraService = cameraService;
        this.emissionsService = emissionsService;
        this.vehicleDetectionRepository = vehicleDetectionRepository;
        this.cameraStatePublisher = cameraStatePublisher;
    }

    @Transactional
    public CameraStateResponse registerDetection(VehicleDetectionRequest request) {
        validateColor(request.color());

        Camera camera = cameraService.getByNombre(request.camara());
        VehicleType vehicleType = VehicleType.fromValue(request.tipo());
        double secondsInScene = request.segundosEnPantalla();

        Emissions historicalEmissions = emissionsService.calculate(vehicleType, secondsInScene);
        saveDetection(request, historicalEmissions, vehicleType);

        ActiveVehicle activeVehicle = new ActiveVehicle(
                request.color(),
                vehicleType,
                historicalEmissions.co2() / secondsInScene,
                historicalEmissions.nox() / secondsInScene,
                historicalEmissions.pm25() / secondsInScene,
                secondsInScene);

        activePoolByCamera
                .computeIfAbsent(camera.getNombre(), key -> new CopyOnWriteArrayList<>())
                .add(activeVehicle);

        CameraStateResponse cameraState = recalcularEstadoCamara(camera.getNombre());
        CameraStateResponse stateToEmit = new CameraStateResponse(
                cameraState.camara(),
                cameraState.lat(),
                cameraState.lng(),
                new ActiveVehicleResponse(
                        activeVehicle.getId(),
                        activeVehicle.getColor(),
                        activeVehicle.getTipo().value(),
                        round2(activeVehicle.getTtl())),
                cameraState.opacidadHumo(),
                SMOKE_COLOR,
                cameraState.emisiones());
        cameraStatePublisher.publish(stateToEmit);
        return stateToEmit;
    }

    @Scheduled(fixedRate = 1000)
    public void tickVehicles() {
        Set<String> cameraNames = activePoolByCamera.keySet();
        for (String cameraName : cameraNames) {
            CopyOnWriteArrayList<ActiveVehicle> activeVehicles = activePoolByCamera.get(cameraName);
            if (activeVehicles == null || activeVehicles.isEmpty()) {
                cameraRuntimeStateByName.put(cameraName, new CameraRuntimeState(Emissions.zero(), 0.0));
                continue;
            }

            for (ActiveVehicle vehicle : activeVehicles) {
                vehicle.decrementTtl(1.0);
            }

            boolean removed = activeVehicles.removeIf(vehicle -> vehicle.getTtl() <= 0.0);

            if (activeVehicles.isEmpty()) {
                activePoolByCamera.remove(cameraName);
            }

            CameraStateResponse updatedState = recalcularEstadoCamara(cameraName);
            if (removed) {
                cameraStatePublisher.publish(updatedState);
            }
        }
    }

    public double getEmisionesPorUbicacion(double lat, double lng) {
        return getEmisionesTotalesPorUbicacion(lat, lng).co2();
    }

    public Emissions getEmisionesTotalesPorUbicacion(double lat, double lng) {
        List<CameraResponse> cameras = cameraService.getAll();
        if (cameras.isEmpty()) {
            return Emissions.zero();
        }

        CameraResponse nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (CameraResponse camera : cameras) {
            double distanceSquared = distanceSquared(lat, lng, camera.lat(), camera.lng());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = camera;
            }
        }

        if (nearest == null) {
            return Emissions.zero();
        }

        CameraRuntimeState runtimeState = cameraRuntimeStateByName.get(nearest.nombre());
        return runtimeState == null ? Emissions.zero() : runtimeState.emissions();
    }

    private void saveDetection(VehicleDetectionRequest request, Emissions emissions, VehicleType vehicleType) {
        VehicleDetection detection = new VehicleDetection();
        detection.setCamara(request.camara());
        detection.setTimestamp(request.timestamp());
        detection.setTipo(vehicleType.value());
        detection.setColor(request.color());
        detection.setSegundosEnPantalla(request.segundosEnPantalla());
        detection.setCo2(emissions.co2());
        detection.setNox(emissions.nox());
        detection.setPm25(emissions.pm25());
        vehicleDetectionRepository.save(detection);
    }

    private CameraStateResponse recalcularEstadoCamara(String cameraName) {
        Camera camera = cameraService.getByNombre(cameraName);
        CopyOnWriteArrayList<ActiveVehicle> activeVehicles = activePoolByCamera.getOrDefault(cameraName, new CopyOnWriteArrayList<>());
        Emissions total = Emissions.zero();

        for (ActiveVehicle activeVehicle : activeVehicles) {
            Emissions contribution = new Emissions(
                    activeVehicle.getCo2PerSecond() * Math.max(activeVehicle.getTtl(), 0.0),
                    activeVehicle.getNoxPerSecond() * Math.max(activeVehicle.getTtl(), 0.0),
                    activeVehicle.getPm25PerSecond() * Math.max(activeVehicle.getTtl(), 0.0));
            total = total.add(contribution);
        }

        Emissions roundedTotal = new Emissions(round2(total.co2()), round2(total.nox()), round2(total.pm25()));
        double opacity = round2(Math.min(1.0, roundedTotal.co2() / OPACITY_CO2_SCALE));
        CameraRuntimeState runtimeState = new CameraRuntimeState(roundedTotal, opacity);
        cameraRuntimeStateByName.put(cameraName, runtimeState);
        return new CameraStateResponse(
                camera.getNombre(),
                camera.getLat(),
                camera.getLng(),
                null,
                runtimeState.opacity(),
                SMOKE_COLOR,
                runtimeState.emissions());
    }

    private void validateColor(String color) {
        if (!HEX_COLOR_PATTERN.matcher(color).matches()) {
            throw new IllegalArgumentException("Color invalido. Se espera formato #RRGGBB");
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double distanceSquared(double lat1, double lng1, double lat2, double lng2) {
        double dLat = lat1 - lat2;
        double dLng = lng1 - lng2;
        return dLat * dLat + dLng * dLng;
    }

    private record CameraRuntimeState(Emissions emissions, double opacity) {}
}
