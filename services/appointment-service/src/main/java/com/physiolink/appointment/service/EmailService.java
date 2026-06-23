package com.physiolink.appointment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.from:noreply@physiolink.dev}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Async
    public void sendReminder(String toEmail, String patientName, String scheduledAt) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(toEmail);
            msg.setSubject("PhysioLink — Appointment Reminder");
            msg.setText(String.format(
                    "Hi %s,\n\nThis is a reminder for your physiotherapy appointment scheduled at %s.\n\n" +
                    "Please join on time via the PhysioLink platform.\n\nTake care,\nPhysioLink Team",
                    patientName, scheduledAt));
            mailSender.send(msg);
            log.info("Reminder sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send reminder to {}: {}", toEmail, e.getMessage());
        }
    }
}
