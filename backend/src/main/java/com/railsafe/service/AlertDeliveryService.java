package com.railsafe.service;

import com.railsafe.entity.NotificationPreference;
import com.railsafe.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional email/SMS delivery.
 *
 * Both channels are disabled by default and stay disabled unless the operator
 * configures the mail server / SMS webhook AND the user opts in. Failures are logged
 * and never block the in-app notification.
 */
@Service
public class AlertDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(AlertDeliveryService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final RestClient restClient;

    @Value("${railsafe.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${railsafe.notifications.email.from:no-reply@railsafe.local}")
    private String emailFrom;

    @Value("${railsafe.notifications.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${railsafe.notifications.sms.webhook-url:}")
    private String smsWebhookUrl;

    @Value("${railsafe.notifications.sms.api-key:}")
    private String smsApiKey;

    public AlertDeliveryService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                @Qualifier("liveRestClient") RestClient restClient) {
        this.mailSenderProvider = mailSenderProvider;
        this.restClient = restClient;
    }

    public boolean isEmailConfigured() {
        return emailEnabled && mailSenderProvider.getIfAvailable() != null;
    }

    public boolean isSmsConfigured() {
        return smsEnabled && smsWebhookUrl != null && !smsWebhookUrl.isBlank();
    }

    public void deliver(User user, NotificationPreference preference, String title, String message) {
        if (user == null || preference == null) {
            return;
        }
        if (preference.isEmailEnabled() && isEmailConfigured()) {
            sendEmail(user, title, message);
        }
        if (preference.isSmsEnabled() && isSmsConfigured()
                && user.getPhone() != null && !user.getPhone().isBlank()) {
            sendSms(user, title, message);
        }
    }

    private void sendEmail(User user, String title, String message) {
        try {
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(emailFrom);
            mail.setTo(user.getEmail());
            mail.setSubject("[RailSafe] " + title);
            mail.setText(message + System.lineSeparator() + System.lineSeparator()
                    + "RailSafe alerts are informational. Never bypass a closed barrier. "
                    + "Always obey railway signals and authorised staff.");
            sender.send(mail);
        } catch (Exception ex) {
            log.warn("Optional email alert could not be sent: {}", ex.getMessage());
        }
    }

    private void sendSms(User user, String title, String message) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("to", user.getPhone());
            payload.put("message", title + " - " + message);
            var request = restClient.post().uri(smsWebhookUrl).body(payload);
            if (smsApiKey != null && !smsApiKey.isBlank()) {
                request = request.header("X-API-Key", smsApiKey);
            }
            request.retrieve().toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Optional SMS alert could not be sent: {}", ex.getMessage());
        }
    }
}
