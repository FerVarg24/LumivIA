package com.lumivia.flood;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Servicio para leer datos de elevacion desde un archivo GeoTIFF (SRTM DEM).
 * Identifica zonas bajas propensas a inundacion.
 *
 * Lee el GeoTIFF directamente usando ImageIO con metadatos de georreferencia.
 */
@Service
public class ElevationService {

    private static final Logger log = LoggerFactory.getLogger(ElevationService.class);

    private final String demFile;

    // Raster de elevacion
    private Raster elevationRaster;
    private boolean enabled;

    // Georreferencia del DEM (extraido del GeoTIFF metadata o hardcoded para SRTM de CDMX)
    // Basado en gdalinfo output del archivo cdmx_dem.tif:
    // Upper Left  (-100.2512500,  20.2770833)
    // Lower Right ( -97.9140278,  18.4990278)
    // Pixel Size = (0.000277777777778,-0.000277777777778)
    private double originLng = -100.2512500;
    private double originLat = 20.2770833;
    private double pixelSizeLng = 0.000277777777778;
    private double pixelSizeLat = -0.000277777777778; // Negativo porque Y decrece hacia abajo

    // Estadisticas de elevacion para CDMX
    private double minElevation = 2200.0;
    private double maxElevation = 3900.0;

    public ElevationService(@Value("${lumivia.flood.dem-file:./data/elevation/cdmx_dem.tif}") String demFile) {
        this.demFile = demFile;
    }

    @PostConstruct
    public void init() {
        Path demPath = Path.of(demFile);
        if (!Files.exists(demPath)) {
            enabled = false;
            log.warn("ElevationService deshabilitado: no se encontro archivo DEM en {}. " +
                    "El riesgo de inundacion solo usara reportes de usuarios.", demPath.toAbsolutePath());
            return;
        }

        try {
            File file = demPath.toFile();
            
            // Usar ImageIO para leer el TIFF
            ImageInputStream iis = ImageIO.createImageInputStream(file);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            
            if (!readers.hasNext()) {
                throw new RuntimeException("No se encontro lector de TIFF");
            }
            
            ImageReader reader = readers.next();
            reader.setInput(iis);
            
            BufferedImage image = reader.read(0);
            elevationRaster = image.getRaster();
            
            reader.dispose();
            iis.close();
            
            enabled = true;

            // Calcular estadisticas de elevacion
            calculateElevationStats();

            log.info("ElevationService habilitado con DEM {}. Tamano: {}x{}. Rango elevacion: {}m - {}m",
                    demFile, elevationRaster.getWidth(), elevationRaster.getHeight(),
                    Math.round(minElevation), Math.round(maxElevation));
        } catch (Exception ex) {
            enabled = false;
            log.error("ElevationService deshabilitado por error cargando DEM: {}", ex.getMessage());
        }
    }

    /**
     * Obtiene la elevacion en metros para un punto dado.
     * @return elevacion en metros, o -1 si no disponible
     */
    public double getElevation(double lat, double lng) {
        if (!enabled || elevationRaster == null) {
            return -1.0;
        }

        try {
            // Convertir coordenadas geograficas a pixel
            int pixelX = (int) Math.round((lng - originLng) / pixelSizeLng);
            int pixelY = (int) Math.round((lat - originLat) / pixelSizeLat);

            // Verificar limites
            if (pixelX < 0 || pixelX >= elevationRaster.getWidth() ||
                pixelY < 0 || pixelY >= elevationRaster.getHeight()) {
                return -1.0;
            }

            // Obtener valor de elevacion
            double elevation = elevationRaster.getSampleDouble(pixelX, pixelY, 0);

            // SRTM usa -32768 como NoData
            if (elevation <= -32000) {
                return -1.0;
            }

            return elevation;
        } catch (Exception ex) {
            log.debug("Error obteniendo elevacion para ({}, {}): {}", lat, lng, ex.getMessage());
            return -1.0;
        }
    }

    /**
     * Calcula el riesgo basado en elevacion (0.0 - 1.0).
     * Zonas mas bajas = mayor riesgo de inundacion.
     *
     * @return riesgo entre 0.0 (zona elevada, sin riesgo) y 1.0 (zona baja, alto riesgo)
     */
    public double getElevationRisk(double lat, double lng) {
        double elevation = getElevation(lat, lng);

        if (elevation < 0) {
            return 0.0; // Sin datos, asumir sin riesgo
        }

        // Normalizar: zonas bajas = riesgo alto
        // Para CDMX urbano: ~2200m (minimo en zonas bajas) a ~2500m (zonas tipicas)
        double lowThreshold = minElevation + 50;  // Zonas muy bajas (alto riesgo)
        double highThreshold = minElevation + 300; // Zonas "seguras" (sin riesgo)

        if (elevation <= lowThreshold) {
            return 1.0; // Maximo riesgo
        }
        if (elevation >= highThreshold) {
            return 0.0; // Sin riesgo por elevacion
        }

        // Interpolacion lineal
        return 1.0 - ((elevation - lowThreshold) / (highThreshold - lowThreshold));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getMinElevation() {
        return minElevation;
    }

    public double getMaxElevation() {
        return maxElevation;
    }

    private void calculateElevationStats() {
        // Muestrear puntos dentro de CDMX para estimar min/max
        double sampleMinLat = 19.2;
        double sampleMaxLat = 19.6;
        double sampleMinLng = -99.3;
        double sampleMaxLng = -99.0;
        double step = 0.02;

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int validSamples = 0;

        for (double lat = sampleMinLat; lat <= sampleMaxLat; lat += step) {
            for (double lng = sampleMinLng; lng <= sampleMaxLng; lng += step) {
                double elev = getElevation(lat, lng);
                if (elev > 0) {
                    min = Math.min(min, elev);
                    max = Math.max(max, elev);
                    validSamples++;
                }
            }
        }

        if (validSamples > 0) {
            this.minElevation = min;
            this.maxElevation = max;
            log.debug("Estadisticas DEM calculadas de {} muestras: min={}m, max={}m",
                    validSamples, Math.round(min), Math.round(max));
        }
    }
}
