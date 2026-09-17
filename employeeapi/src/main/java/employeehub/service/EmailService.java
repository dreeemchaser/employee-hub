package employeehub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails. Delivery is config-gated: unless {@code app.mail.enabled}
 * is true and a {@link JavaMailSender} is available (auto-configured when
 * {@code spring.mail.host} is set), send calls are a safe no-op. This lets the
 * application run in CI and local development without an SMTP server.
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:no-reply@employeehub.local}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
    }

    /**
     * Send a plain-text email. When mail is disabled or no mail sender is
     * configured, this logs and returns without throwing, so callers (e.g. the
     * password-reset flow) are never broken by a missing SMTP setup.
     */
    public void sendPlainText(String to, String subject, String body) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!enabled || sender == null) {
            log.info("Email delivery disabled; skipping email to {} (subject: {})", to, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception ex) {
            // Do not fail the caller's request because email delivery failed.
            log.error("Failed to send email to {}: {}", to, subject, ex);
        }
    }
}
