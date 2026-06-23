package com.physiolink.video.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for generating MinIO pre-signed URLs for video recording upload/download.
 */
@Service
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    private final MinioClient minioClient;
    private final String bucket;

    public RecordingService(MinioClient minioClient,
                            @Value("${minio.bucket.recordings:recordings}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    /**
     * Generate a pre-signed PUT URL so the client can upload directly to MinIO.
     *
     * @param objectName e.g. "sessions/<roomId>/recording.webm"
     * @return pre-signed upload URL valid for 1 hour
     */
    public String getUploadUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate upload URL: {}", e.getMessage());
            throw new RuntimeException("Could not generate upload URL", e);
        }
    }

    /**
     * Generate a pre-signed GET URL for playback.
     *
     * @param objectName e.g. "sessions/<roomId>/recording.webm"
     * @return pre-signed download URL valid for 6 hours
     */
    public String getDownloadUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(6, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate download URL: {}", e.getMessage());
            throw new RuntimeException("Could not generate download URL", e);
        }
    }
}
