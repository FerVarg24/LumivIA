package com.lumivia.websocket;

import com.lumivia.vehicle.CameraStateResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class CameraStatePublisher {

    private static final String TOPIC = "/topic/camaras";

    private final SimpMessagingTemplate messagingTemplate;

    public CameraStatePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(CameraStateResponse cameraStateResponse) {
        messagingTemplate.convertAndSend(TOPIC, cameraStateResponse);
    }
}
