package com.physiolink.appointment.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Internal HTTP client that fetches patient profile data from the patient-service.
 * Used by the appointment-service to get real patient names and emails for emails.
 */
@Component
public class PatientServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PatientServiceClient.class);

    private final RestClient restClient;

    public PatientServiceClient(
            @Value("${services.patient.url:http://localhost:8081}") String patientServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(patientServiceUrl)
                .build();
    }

    /**
     * Holds the fields we need from the patient-service response.
     */
    public record PatientInfo(String name, String email) {}

    /**
     * Fetches the patient's name and email from the patient-service.
     * The request is sent with an X-User-Id / X-User-Role header pair that mimics
     * the API gateway's behaviour, so the patient-service authorises it as a PHYSIO
     * (the only role allowed to call GET /api/patients/{id}).
     *
     * @param patientId   the UUID of the patient to look up
     * @param callerUserId the userId of the booking caller (forwarded as X-User-Id)
     * @param callerRole   the role of the booking caller (forwarded as X-User-Role)
     * @return PatientInfo on success, or a fallback with a placeholder name/email on error
     */
    public PatientInfo fetchPatientInfo(UUID patientId, String callerUserId, String callerRole) {
        try {
            var response = restClient.get()
                    .uri("/api/patients/{id}", patientId)
                    .header("X-User-Id", callerUserId)
                    .header("X-User-Role", callerRole)
                    .retrieve()
                    .body(PatientResponse.class);

            if (response != null && response.name() != null && response.email() != null) {
                return new PatientInfo(response.name(), response.email());
            }
        } catch (Exception ex) {
            log.warn("Could not fetch patient info for {} from patient-service: {}", patientId, ex.getMessage());
        }

        // Graceful fallback — email still sends, just with a placeholder
        return new PatientInfo("Patient", "patient-" + patientId + "@physiolink.dev");
    }

    // ── Internal DTO matching the patient-service JSON shape ─────────────────
    private record PatientResponse(
            String id,
            String email,
            String name,
            String phone
    ) {}
}
