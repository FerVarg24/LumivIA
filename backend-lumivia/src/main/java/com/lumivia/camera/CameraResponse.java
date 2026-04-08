package com.lumivia.camera;

public record CameraResponse(String nombre, double lat, double lng, String descripcion) {

    public static CameraResponse from(Camera camera) {
        return new CameraResponse(camera.getNombre(), camera.getLat(), camera.getLng(), camera.getDescripcion());
    }
}
