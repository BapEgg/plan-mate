package com.planmate.auth.email;

import com.planmate.auth.exception.EmailSendFailedException;
import com.planmate.user.entity.UserEntity;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "gmail-smtp")
public class GmailSmtpAuthEmailSender implements AuthEmailSender {

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;
    private final String from;

    public GmailSmtpAuthEmailSender(
            JavaMailSender mailSender,
            @Value("${app.frontend.base-url}") String frontendBaseUrl,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.from = from;
    }

    @Override
    public void sendSignupVerification(UserEntity user, String rawToken) {
        validateFromAddress();
        String verificationUrl = frontendBaseUrl + "/auth/email-verification?token=" + rawToken;
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(user.getEmail());
            helper.setSubject("[PlanMate] Verify your email");
            helper.setText("""
                    Complete your PlanMate email verification using the link below.

                    %s

                    If you did not request this email, you can ignore it.
                    """.formatted(verificationUrl), false);

            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new EmailSendFailedException(exception);
        }
    }

    private void validateFromAddress() {
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("app.mail.from must be configured when app.mail.provider=gmail-smtp.");
        }
    }

}
