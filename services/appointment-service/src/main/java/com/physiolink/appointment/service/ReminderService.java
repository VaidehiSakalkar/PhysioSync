package com.physiolink.appointment.service;

import com.physiolink.appointment.entity.Appointment;
import com.physiolink.appointment.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled service that scans upcoming appointments and sends email reminders.
 * Redis is used as a deduplication store so we never send the same reminder twice.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final String REDIS_KEY_PREFIX = "reminder:sent:";
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z");

    private final AppointmentRepository appointmentRepo;
    private final EmailService emailService;
    private final StringRedisTemplate redis;
    private final int windowHours;

    public ReminderService(AppointmentRepository appointmentRepo,
                           EmailService emailService,
                           StringRedisTemplate redis,
                           @Value("${reminder.window-hours:24}") int windowHours) {
        this.appointmentRepo = appointmentRepo;
        this.emailService = emailService;
        this.redis = redis;
        this.windowHours = windowHours;
    }

    @Scheduled(cron = "${reminder.cron:0 */15 * * * *}")
    public void sendReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime until = now.plusHours(windowHours);
        List<Appointment> upcoming = appointmentRepo.findUpcoming(now, until);

        log.info("Reminder job: found {} upcoming appointments", upcoming.size());

        for (Appointment appt : upcoming) {
            String redisKey = REDIS_KEY_PREFIX + appt.getId();
            if (Boolean.TRUE.equals(redis.hasKey(redisKey))) {
                continue;  // already sent
            }
            // Mark as sent first (idempotency — TTL slightly longer than window)
            redis.opsForValue().set(redisKey, "1", windowHours + 1, TimeUnit.HOURS);

            // In a real system we'd fetch email/name from patient-service.
            // For now we log the appointment ID and send a placeholder.
            emailService.sendReminder(
                    "patient-" + appt.getPatientId() + "@physiolink.dev",
                    "Patient",
                    appt.getScheduledAt().format(DISPLAY_FMT));
        }
    }
}
