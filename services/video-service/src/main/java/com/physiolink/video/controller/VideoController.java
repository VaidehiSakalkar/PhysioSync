package com.physiolink.video.controller;

import com.physiolink.video.service.RecordingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final RecordingService recordingService;
    private final List<Map<String, String>> iceServers;

    public VideoController(RecordingService recordingService,
                           @Value("${webrtc.ice-servers[0].urls}") String stunUrl1,
                           @Value("${webrtc.ice-servers[1].urls}") String stunUrl2) {
        this.recordingService = recordingService;
        this.iceServers = List.of(
                Map.of("urls", stunUrl1),
                Map.of("urls", stunUrl2)
        );
    }

    /**
     * GET /api/video/ice-config
     * Returns STUN/TURN server configuration for the frontend RTCPeerConnection.
     */
    @GetMapping("/ice-config")
    public ResponseEntity<Map<String, Object>> iceConfig() {
        return ResponseEntity.ok(Map.of("iceServers", iceServers));
    }

    /**
     * POST /api/video/recordings/upload-url
     * Body: { "objectName": "sessions/<roomId>/recording.webm" }
     * Returns a pre-signed MinIO PUT URL.
     */
    @PostMapping("/recordings/upload-url")
    public ResponseEntity<Map<String, String>> uploadUrl(@RequestBody Map<String, String> body) {
        String url = recordingService.getUploadUrl(body.get("objectName"));
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * POST /api/video/recordings/download-url
     * Body: { "objectName": "sessions/<roomId>/recording.webm" }
     * Returns a pre-signed MinIO GET URL.
     */
    @PostMapping("/recordings/download-url")
    public ResponseEntity<Map<String, String>> downloadUrl(@RequestBody Map<String, String> body) {
        String url = recordingService.getDownloadUrl(body.get("objectName"));
        return ResponseEntity.ok(Map.of("url", url));
    }
}
