package com.lumivia.routing;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.lumivia.camera.CameraResponse;
import com.lumivia.camera.CameraService;
import com.lumivia.emissions.Emissions;
import com.lumivia.flood.FloodRiskService;
import com.lumivia.vehicle.VehicleDetection;
import com.lumivia.vehicle.VehicleDetectionRepository;
import com.lumivia.vehicle.VehicleDetectionService;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.graphhopper.json.Statement.Op.LIMIT;

@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private static final double CDMX_MIN_LAT = 19.0;
    private static final double CDMX_MAX_LAT = 19.7;
    private static final double CDMX_MIN_LNG = -99.4;
    private static final double CDMX_MAX_LNG = -98.9;

    private static final double HEALTHY_CO2_WEIGHT = 5000.0;
    private static final double ACTIVE_CO2_SWITCH_THRESHOLD = 200.0;
    private static final double HISTORICAL_CO2_SWITCH_THRESHOLD = 200.0;
    private static final int HISTORICAL_LOOKBACK_DAYS = 30;

    private static final int IDW_NEIGHBORS = 4;
    private static final double IDW_POWER = 2.0;
    private static final double DIST_EPS_METERS = 15.0;
    private static final double CAMERA_INFLUENCE_RADIUS_METERS = 50.0;
    private static final double SEGMENT_NORMALIZATION_METERS = 100.0;

    private final VehicleDetectionService vehicleDetectionService;
    private final VehicleDetectionRepository vehicleDetectionRepository;
    private final CameraService cameraService;
    private final FloodRiskService floodRiskService;
    private final String osmFile;
    private final String graphCache;

    private GraphHopper graphHopper;
    private volatile boolean enabled;

    public RouteService(
            VehicleDetectionService vehicleDetectionService,
            VehicleDetectionRepository vehicleDetectionRepository,
            CameraService cameraService,
            FloodRiskService floodRiskService,
            @Value("${lumivia.osm-file:./data/cdmx.osm.pbf}") String osmFile,
            @Value("${lumivia.graph-cache:./data/graph-cache}") String graphCache) {
        this.vehicleDetectionService = vehicleDetectionService;
        this.vehicleDetectionRepository = vehicleDetectionRepository;
        this.cameraService = cameraService;
        this.floodRiskService = floodRiskService;
        this.osmFile = osmFile;
        this.graphCache = graphCache;
    }

    @PostConstruct
    public void init() {
        Path osmPath = Path.of(osmFile);
        if (!Files.exists(osmPath)) {
            enabled = false;
            log.warn("Routing deshabilitado: no se encontro archivo OSM en {}. Coloca cdmx.osm.pbf en esa ruta.", osmPath.toAbsolutePath());
            return;
        }

        try {
            GraphHopper hopper = new GraphHopper();
            hopper.setOSMFile(osmFile);
            hopper.setGraphHopperLocation(graphCache);
            hopper.setEncodedValuesString("car_average_speed,foot_average_speed");
            hopper.setProfiles(
                    buildCustomProfile("conductor", "car"),
                    buildCustomProfile("peaton", "foot"),
                    buildCustomProfile("combinada", "car"));
            hopper.getCHPreparationHandler()
                    .setCHProfiles(new CHProfile("conductor"), new CHProfile("peaton"), new CHProfile("combinada"));
            hopper.importOrLoad();
            this.graphHopper = hopper;
            this.enabled = true;
            log.info("Routing habilitado con OSM {} y cache {}", osmFile, graphCache);
        } catch (Exception ex) {
            enabled = false;
            log.error("Routing deshabilitado por error inicializando GraphHopper", ex);
        }
    }

    public RouteResponse calculate(RouteRequest request) {
        ensureEnabled();
        validateBounds(request.origen().lat(), request.origen().lng(), "origen");
        validateBounds(request.destino().lat(), request.destino().lng(), "destino");

        boolean isRaining = request.isRaining();
        List<CameraResponse> cameras = cameraService.getAll();
        Map<String, Emissions> historicalByCamera = loadHistoricalAverages(cameras);
        List<CameraNode> cameraNodes = buildCameraNodes(cameras, historicalByCamera);

        ResponsePath rutaRapida = calculatePath(request, false, request.perfil(), cameraNodes, isRaining);
        PathMetrics metricsRapida = calculatePathMetrics(rutaRapida.getPoints(), cameraNodes);

        boolean activeHigh = metricsRapida.active().co2() >= ACTIVE_CO2_SWITCH_THRESHOLD;
        boolean historicalHigh = metricsRapida.historical().co2() >= HISTORICAL_CO2_SWITCH_THRESHOLD;
        boolean shouldSearchAlternative = activeHigh || historicalHigh;

        ResponsePath rutaSaludable = rutaRapida;
        PathMetrics metricsSaludable = metricsRapida;
        boolean alternativeFound = false;
        boolean alternativesDiscarded = false;

        if (shouldSearchAlternative) {
            ResponsePath candidate = calculatePath(request, true, request.perfil(), cameraNodes, isRaining);
            if (!areSameCoordinates(toLngLat(candidate.getPoints()), toLngLat(rutaRapida.getPoints()))) {
                PathMetrics candidateMetrics = calculatePathMetrics(candidate.getPoints(), cameraNodes);
                if (isHealthierThanBase(metricsRapida, candidateMetrics)) {
                    rutaSaludable = candidate;
                    metricsSaludable = candidateMetrics;
                    alternativeFound = true;
                } else {
                    alternativesDiscarded = true;
                }
            } else {
                Optional<ResponsePath> detour = findDetourAlternative(request, rutaRapida, request.perfil(), cameraNodes, isRaining);
                if (detour.isPresent()) {
                    PathMetrics detourMetrics = calculatePathMetrics(detour.get().getPoints(), cameraNodes);
                    if (isHealthierThanBase(metricsRapida, detourMetrics)) {
                        rutaSaludable = detour.get();
                        metricsSaludable = detourMetrics;
                        alternativeFound = true;
                    } else {
                        alternativesDiscarded = true;
                    }
                }
            }
        }

        int tiempoExtraMin = Math.max(0,
                (int) Math.round(rutaSaludable.getTime() / 60000.0) - (int) Math.round(rutaRapida.getTime() / 60000.0));
        double ahorroCo2 = Math.max(0.0, round2(metricsRapida.total().co2() - metricsSaludable.total().co2()));
        double ahorroPct = metricsRapida.total().co2() <= 0.0
                ? 0.0
                : round2((ahorroCo2 / metricsRapida.total().co2()) * 100.0);

        String descripcionRapida = fastDescription(metricsRapida, activeHigh, historicalHigh, isRaining);
        String descripcionSaludable = healthyDescription(
                shouldSearchAlternative,
                alternativeFound,
                activeHigh,
                historicalHigh,
                tiempoExtraMin,
                ahorroCo2,
                ahorroPct,
                metricsRapida,
                metricsSaludable,
                alternativesDiscarded,
                isRaining);

        RouteResponse.RouteOption optionRapida = buildOption(rutaRapida, metricsRapida.total(), descripcionRapida);
        RouteResponse.RouteOption optionSaludable = buildOption(rutaSaludable, metricsSaludable.total(), descripcionSaludable);

        return new RouteResponse(optionRapida, optionSaludable, ahorroCo2, tiempoExtraMin, isRaining);
    }

    private void ensureEnabled() {
        if (!enabled || graphHopper == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Modulo routing deshabilitado. Coloca cdmx.osm.pbf en la ruta configurada en lumivia.osm-file");
        }
    }

    private void validateBounds(double lat, double lng, String field) {
        if (!inBounds(lat, lng)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " fuera del bounding box de CDMX");
        }
    }

    private boolean inBounds(double lat, double lng) {
        return lat >= CDMX_MIN_LAT && lat <= CDMX_MAX_LAT && lng >= CDMX_MIN_LNG && lng <= CDMX_MAX_LNG;
    }

    private String profileName(RouteProfile routeProfile) {
        return switch (routeProfile) {
            case PEATON -> "peaton";
            case CONDUCTOR -> "conductor";
            case COMBINADA -> "combinada";
        };
    }

    private ResponsePath calculatePath(
            RouteRequest request,
            boolean saludable,
            RouteProfile profile,
            List<CameraNode> cameraNodes,
            boolean isRaining) {
        GHRequest ghRequest = new GHRequest(
                        request.origen().lat(),
                        request.origen().lng(),
                        request.destino().lat(),
                        request.destino().lng())
                .setProfile(profileName(profile))
                .setLocale(Locale.ROOT);

        if (saludable) {
            ghRequest.setAlgorithm("alternative_route");
            ghRequest.putHint("ch.disable", true);
            ghRequest.putHint("lm.disable", true);
            ghRequest.putHint("alternative_route.max_weight_factor", 25.0);
            ghRequest.putHint("alternative_route.max_paths", 8);
            ghRequest.putHint("alternative_route.max_share_factor", 0.95);
        }

        GHResponse response = graphHopper.route(ghRequest);
        if (response.hasErrors()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GraphHopper no encontro ruta entre origen y destino");
        }

        if (saludable && response.getAll().size() > 1) {
            return pickHealthiestAlternative(response.getAll(), profile, cameraNodes, isRaining);
        }

        ResponsePath best = response.getBest();
        if (best == null || best.getPoints() == null || best.getPoints().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GraphHopper no encontro ruta entre origen y destino");
        }
        return best;
    }

    private ResponsePath pickHealthiestAlternative(
            List<ResponsePath> candidates,
            RouteProfile profile,
            List<CameraNode> cameraNodes,
            boolean isRaining) {
        ResponsePath winner = candidates.get(0);
        double bestWeight = weightedExposure(candidates.get(0).getPoints(), profile, cameraNodes, isRaining);

        for (int i = 1; i < candidates.size(); i++) {
            ResponsePath path = candidates.get(i);
            double weight = weightedExposure(path.getPoints(), profile, cameraNodes, isRaining);
            if (weight < bestWeight) {
                bestWeight = weight;
                winner = path;
            }
        }
        return winner;
    }

    private Optional<ResponsePath> findDetourAlternative(
            RouteRequest request,
            ResponsePath baseline,
            RouteProfile profile,
            List<CameraNode> cameraNodes,
            boolean isRaining) {
        List<GHPoint> detours = buildDetourWaypoints(
                request.origen().lat(), request.origen().lng(), request.destino().lat(), request.destino().lng());

        ResponsePath best = null;
        double bestWeight = Double.MAX_VALUE;

        for (GHPoint detour : detours) {
            GHRequest candidate = new GHRequest(List.of(
                    new GHPoint(request.origen().lat(), request.origen().lng()),
                    detour,
                    new GHPoint(request.destino().lat(), request.destino().lng())));
            candidate.setProfile(profileName(profile));
            candidate.setLocale(Locale.ROOT);
            candidate.putHint("ch.disable", true);
            candidate.putHint("lm.disable", true);

            GHResponse response = graphHopper.route(candidate);
            if (response.hasErrors()) {
                continue;
            }

            ResponsePath path = response.getBest();
            if (path == null || path.getPoints() == null || path.getPoints().isEmpty()) {
                continue;
            }

            if (areSameCoordinates(toLngLat(path.getPoints()), toLngLat(baseline.getPoints()))) {
                continue;
            }

            double weight = weightedExposure(path.getPoints(), profile, cameraNodes, isRaining);
            if (weight < bestWeight) {
                bestWeight = weight;
                best = path;
            }
        }

        return Optional.ofNullable(best);
    }

    private List<GHPoint> buildDetourWaypoints(double originLat, double originLng, double destinationLat, double destinationLng) {
        List<GHPoint> waypoints = new ArrayList<>();

        double midLat = (originLat + destinationLat) / 2.0;
        double midLng = (originLng + destinationLng) / 2.0;
        double dLat = destinationLat - originLat;
        double dLng = destinationLng - originLng;
        double length = Math.sqrt(dLat * dLat + dLng * dLng);
        if (length < 1e-9) {
            return waypoints;
        }

        double perpLat = -dLng / length;
        double perpLng = dLat / length;
        double[] offsets = {0.0015, -0.0015, 0.0025, -0.0025, 0.0035, -0.0035, 0.0045, -0.0045};

        for (double offset : offsets) {
            double lat = midLat + (perpLat * offset);
            double lng = midLng + (perpLng * offset);
            if (inBounds(lat, lng)) {
                waypoints.add(new GHPoint(lat, lng));
            }
        }
        return waypoints;
    }

    private Profile buildCustomProfile(String name, String vehicle) {
        CustomModel customModel = new CustomModel()
                .addToSpeed(Statement.If("true", LIMIT, vehicle + "_average_speed"));

        return new Profile(name).setWeighting("custom").setCustomModel(customModel);
    }

    private RouteResponse.RouteOption buildOption(ResponsePath path, Emissions emissions, String description) {
        Emissions rounded = new Emissions(round2(emissions.co2()), round2(emissions.nox()), round2(emissions.pm25()));
        return new RouteResponse.RouteOption(
                round2(path.getDistance() / 1000.0),
                (int) Math.round(path.getTime() / 60000.0),
                riskLevel(rounded.co2()),
                toLngLat(path.getPoints()),
                rounded,
                description);
    }

    private PathMetrics calculatePathMetrics(PointList points, List<CameraNode> cameraNodes) {
        Emissions activeTotal = Emissions.zero();
        Emissions historicalTotal = Emissions.zero();
        Map<String, Double> hotspotContributions = new HashMap<>();

        for (int i = 1; i < points.size(); i++) {
            double latA = points.getLat(i - 1);
            double lngA = points.getLon(i - 1);
            double latB = points.getLat(i);
            double lngB = points.getLon(i);

            double segmentMeters = haversineMeters(latA, lngA, latB, lngB);
            double midLat = (latA + latB) / 2.0;
            double midLng = (lngA + lngB) / 2.0;

            InterpolatedSample sample = interpolatePoint(midLat, midLng, cameraNodes);
            double segmentFactor = Math.max(0.1, segmentMeters / SEGMENT_NORMALIZATION_METERS);

            Emissions weightedActive = new Emissions(
                    sample.active().co2() * segmentFactor,
                    sample.active().nox() * segmentFactor,
                    sample.active().pm25() * segmentFactor);
            Emissions weightedHistorical = new Emissions(
                    sample.historical().co2() * segmentFactor,
                    sample.historical().nox() * segmentFactor,
                    sample.historical().pm25() * segmentFactor);

            activeTotal = activeTotal.add(weightedActive);
            historicalTotal = historicalTotal.add(weightedHistorical);

            for (Map.Entry<String, Double> entry : sample.co2ByCamera().entrySet()) {
                hotspotContributions.merge(entry.getKey(), entry.getValue() * segmentFactor, Double::sum);
            }
        }

        String topHotspot = null;
        double topHotspotCo2 = 0.0;
        for (Map.Entry<String, Double> entry : hotspotContributions.entrySet()) {
            if (entry.getValue() > topHotspotCo2) {
                topHotspotCo2 = entry.getValue();
                topHotspot = entry.getKey();
            }
        }

        Emissions total = activeTotal.add(historicalTotal);
        return new PathMetrics(activeTotal, historicalTotal, total, topHotspot, topHotspotCo2);
    }

    private InterpolatedSample interpolatePoint(double lat, double lng, List<CameraNode> cameraNodes) {
        if (cameraNodes.isEmpty()) {
            return new InterpolatedSample(Emissions.zero(), Emissions.zero(), Emissions.zero(), Map.of());
        }

        List<DistanceNode> sorted = new ArrayList<>(cameraNodes.size());
        for (CameraNode node : cameraNodes) {
            double distanceMeters = haversineMeters(lat, lng, node.lat(), node.lng());
            sorted.add(new DistanceNode(node, distanceMeters));
        }
        sorted.sort((a, b) -> Double.compare(a.distanceMeters(), b.distanceMeters()));

        List<DistanceNode> inRadius = new ArrayList<>();
        for (DistanceNode node : sorted) {
            if (node.distanceMeters() <= CAMERA_INFLUENCE_RADIUS_METERS) {
                inRadius.add(node);
            }
        }

        if (inRadius.isEmpty()) {
            return new InterpolatedSample(Emissions.zero(), Emissions.zero(), Emissions.zero(), Map.of());
        }

        int limit = Math.min(IDW_NEIGHBORS, inRadius.size());
        List<DistanceNode> nearest = inRadius.subList(0, limit);
        double nearestDistance = nearest.get(0).distanceMeters();

        Emissions active = Emissions.zero();
        Emissions historical = Emissions.zero();
        Map<String, Double> byCamera = new HashMap<>();

        if (nearestDistance <= DIST_EPS_METERS) {
            CameraNode node = nearest.get(0).node();
            double co2Total = node.active().co2() + node.historical().co2();
            byCamera.put(node.nombre(), co2Total);
            Emissions total = node.active().add(node.historical());
            return new InterpolatedSample(node.active(), node.historical(), total, byCamera);
        }

        double weightSum = 0.0;
        double[] rawWeights = new double[limit];
        for (int i = 0; i < limit; i++) {
            double weight = 1.0 / Math.pow(nearest.get(i).distanceMeters() + 1.0, IDW_POWER);
            rawWeights[i] = weight;
            weightSum += weight;
        }

        for (int i = 0; i < limit; i++) {
            CameraNode node = nearest.get(i).node();
            double weight = rawWeights[i] / weightSum;

            Emissions weightedActive = new Emissions(
                    node.active().co2() * weight,
                    node.active().nox() * weight,
                    node.active().pm25() * weight);
            Emissions weightedHistorical = new Emissions(
                    node.historical().co2() * weight,
                    node.historical().nox() * weight,
                    node.historical().pm25() * weight);

            active = active.add(weightedActive);
            historical = historical.add(weightedHistorical);
            byCamera.merge(node.nombre(), (node.active().co2() + node.historical().co2()) * weight, Double::sum);
        }

        Emissions total = active.add(historical);
        return new InterpolatedSample(active, historical, total, byCamera);
    }

    private Map<String, Emissions> loadHistoricalAverages(List<CameraResponse> cameras) {
        Map<String, Emissions> historicalByCamera = new HashMap<>();
        Instant until = Instant.now();
        Instant since = until.minus(HISTORICAL_LOOKBACK_DAYS, ChronoUnit.DAYS);

        for (CameraResponse camera : cameras) {
            List<VehicleDetection> detections =
                    vehicleDetectionRepository.findByCamaraAndTimestampBetweenOrderByTimestampAsc(camera.nombre(), since, until);
            if (detections.isEmpty()) {
                historicalByCamera.put(camera.nombre(), Emissions.zero());
                continue;
            }

            double co2 = 0.0;
            double nox = 0.0;
            double pm25 = 0.0;
            for (VehicleDetection detection : detections) {
                co2 += detection.getCo2();
                nox += detection.getNox();
                pm25 += detection.getPm25();
            }

            double size = detections.size();
            historicalByCamera.put(camera.nombre(), new Emissions(co2 / size, nox / size, pm25 / size));
        }
        return historicalByCamera;
    }

    private List<CameraNode> buildCameraNodes(List<CameraResponse> cameras, Map<String, Emissions> historicalByCamera) {
        List<CameraNode> nodes = new ArrayList<>(cameras.size());
        for (CameraResponse camera : cameras) {
            Emissions active = vehicleDetectionService.getEmisionesTotalesPorUbicacion(camera.lat(), camera.lng());
            Emissions historical = historicalByCamera.getOrDefault(camera.nombre(), Emissions.zero());
            Emissions filteredHistorical = active.co2() > 0.0 ? historical : Emissions.zero();
            nodes.add(new CameraNode(camera.nombre(), camera.lat(), camera.lng(), active, filteredHistorical));
        }
        return nodes;
    }

    private String fastDescription(PathMetrics metrics, boolean activeHigh, boolean historicalHigh, boolean isRaining) {
        String hotspot = hotspotSuffix(metrics);
        String rainingSuffix = isRaining ? " [Modo lluvia activo - evitando zonas inundables]" : "";
        if (metrics.total().co2() <= 0.0) {
            return "Ruta mas corta con carga baja: fuera del radio de influencia de camaras activas." + rainingSuffix;
        }
        if (activeHigh && historicalHigh) {
            return "Ruta mas corta con carga alta: CO2 actual " + fmt(metrics.active().co2())
                    + " g y CO2 historico " + fmt(metrics.historical().co2()) + " g." + hotspot + rainingSuffix;
        }
        if (historicalHigh) {
            return "Ruta mas corta por tiempo, pero pasa por calle historicamente alta en emisiones: CO2 historico "
                    + fmt(metrics.historical().co2()) + " g." + hotspot + rainingSuffix;
        }
        if (activeHigh) {
            return "Ruta mas corta con contaminacion activa elevada: CO2 actual "
                    + fmt(metrics.active().co2()) + " g." + hotspot + rainingSuffix;
        }
        return "Ruta mas corta con carga baja: CO2 actual " + fmt(metrics.active().co2())
                + " g y CO2 historico " + fmt(metrics.historical().co2()) + " g." + hotspot + rainingSuffix;
    }

    private String healthyDescription(
            boolean shouldSearchAlternative,
            boolean alternativeFound,
            boolean activeHigh,
            boolean historicalHigh,
            int extraMinutes,
            double ahorroCo2,
            double ahorroPct,
            PathMetrics fastMetrics,
            PathMetrics healthyMetrics,
            boolean alternativesDiscarded,
            boolean isRaining) {
        String rainingSuffix = isRaining ? " [Modo lluvia activo]" : "";
        if (fastMetrics.total().co2() <= 0.0 && healthyMetrics.total().co2() <= 0.0) {
            return "Ruta saludable coincide con ruta rapida: ambas fuera del radio de influencia de camaras activas." + rainingSuffix;
        }
        if (!shouldSearchAlternative) {
            return "Ruta saludable coincide con ruta rapida: no hay beneficio ambiental medible "
                    + "(ahorro " + fmt(ahorroCo2) + " g CO2, +" + extraMinutes + " min)." + rainingSuffix;
        }
        if (alternativesDiscarded) {
            return "Se evaluaron alternativas, pero no mejoran emisiones frente a la ruta rapida. "
                    + "Se mantiene ruta base para evitar desvio innecesario." + rainingSuffix;
        }
        if (!alternativeFound) {
            return "No hay ruta alternativa disponible en esta zona" + rainingSuffix;
        }
        if (historicalHigh && activeHigh) {
            return "Ruta saludable recomendada: reduce " + fmt(ahorroCo2) + " g CO2 (" + fmt(ahorroPct)
                    + "%), evita carga actual e historica, +" + extraMinutes + " min." + hotspotDeltaSuffix(fastMetrics, healthyMetrics) + rainingSuffix;
        }
        if (historicalHigh) {
            return "Ruta saludable recomendada: evita calle historicamente alta en emisiones, ahorro "
                    + fmt(ahorroCo2) + " g CO2 (" + fmt(ahorroPct) + "%), +" + extraMinutes + " min."
                    + hotspotDeltaSuffix(fastMetrics, healthyMetrics) + rainingSuffix;
        }
        return "Ruta saludable recomendada: reduce exposicion actual, ahorro " + fmt(ahorroCo2)
                + " g CO2 (" + fmt(ahorroPct) + "%), +" + extraMinutes + " min."
                + hotspotDeltaSuffix(fastMetrics, healthyMetrics) + rainingSuffix;
    }

    private boolean areSameCoordinates(List<List<Double>> first, List<List<Double>> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            List<Double> a = first.get(i);
            List<Double> b = second.get(i);
            if (Math.abs(a.get(0) - b.get(0)) > 1e-6 || Math.abs(a.get(1) - b.get(1)) > 1e-6) {
                return false;
            }
        }
        return true;
    }

    private List<List<Double>> toLngLat(PointList points) {
        List<List<Double>> coordinates = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            coordinates.add(List.of(round6(points.getLon(i)), round6(points.getLat(i))));
        }
        return coordinates;
    }

    private double weightedExposure(
            PointList points,
            RouteProfile routeProfile,
            List<CameraNode> cameraNodes,
            boolean isRaining) {
        double weight = 0.0;

        for (int i = 1; i < points.size(); i++) {
            double latA = points.getLat(i - 1);
            double lngA = points.getLon(i - 1);
            double latB = points.getLat(i);
            double lngB = points.getLon(i);

            double distanceMeters = haversineMeters(latA, lngA, latB, lngB);
            double midLat = (latA + latB) / 2.0;
            double midLng = (lngA + lngB) / 2.0;

            InterpolatedSample sample = interpolatePoint(midLat, midLng, cameraNodes);
            double co2Acumulado = sample.total().co2();
            double floodPenalty = floodRiskService.calculateRoutePenalty(midLat, midLng, isRaining);

            weight += switch (routeProfile) {
                case PEATON -> distanceMeters + (co2Acumulado * HEALTHY_CO2_WEIGHT);
                case CONDUCTOR -> distanceMeters + floodPenalty;
                case COMBINADA -> distanceMeters + (co2Acumulado * HEALTHY_CO2_WEIGHT) + floodPenalty;
            };
        }
        return weight;
    }

    private String riskLevel(double co2Total) {
        if (co2Total < 200.0) {
            return "bajo";
        }
        if (co2Total < 500.0) {
            return "medio";
        }
        return "alto";
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6_371_000.0 * c;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private boolean isHealthierThanBase(PathMetrics base, PathMetrics candidate) {
        double tolerance = 1.0;
        return candidate.total().co2() + tolerance < base.total().co2();
    }

    private String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String hotspotSuffix(PathMetrics metrics) {
        if (metrics.topHotspot() == null || metrics.topHotspotCo2() <= 0.0) {
            return "";
        }
        return " Foco dominante: " + metrics.topHotspot() + " (" + fmt(metrics.topHotspotCo2()) + " g CO2 estimados).";
    }

    private String hotspotDeltaSuffix(PathMetrics fastMetrics, PathMetrics healthyMetrics) {
        if (fastMetrics.topHotspot() == null) {
            return "";
        }
        if (healthyMetrics.topHotspot() == null || !fastMetrics.topHotspot().equals(healthyMetrics.topHotspot())) {
            return " Se evita el foco principal de " + fastMetrics.topHotspot() + ".";
        }
        double delta = Math.max(0.0, fastMetrics.topHotspotCo2() - healthyMetrics.topHotspotCo2());
        if (delta <= 0.0) {
            return "";
        }
        return " Reduccion local en " + fastMetrics.topHotspot() + ": " + fmt(delta) + " g CO2.";
    }

    private record CameraNode(String nombre, double lat, double lng, Emissions active, Emissions historical) {}

    private record DistanceNode(CameraNode node, double distanceMeters) {}

    private record InterpolatedSample(Emissions active, Emissions historical, Emissions total, Map<String, Double> co2ByCamera) {}

    private record PathMetrics(
            Emissions active,
            Emissions historical,
            Emissions total,
            String topHotspot,
            double topHotspotCo2) {}
}
