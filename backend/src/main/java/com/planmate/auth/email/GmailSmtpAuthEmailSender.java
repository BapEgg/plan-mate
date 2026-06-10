package com.planmate.auth.email;

import com.planmate.auth.exception.EmailSendFailedException;
import com.planmate.user.entity.UserEntity;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
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
        sendPlainText(user.getEmail(), "[PlanMate] 이메일 인증을 완료해 주세요", """
                안녕하세요, PlanMate입니다.

                회원가입을 완료하려면 아래 링크를 클릭해 이메일 인증을 진행해 주세요.

                %s

                본인이 요청하지 않은 메일이라면 이 메일을 무시해 주세요.
                """.formatted(verificationUrl));
    }

    @Override
    public void sendLoginIdRecovery(UserEntity user, String rawToken) {
        validateFromAddress();
        String recoveryUrl = frontendBaseUrl + "/auth/find-login-id?token=" + rawToken;
        sendPlainText(user.getEmail(), "[PlanMate] 아이디 찾기 인증 안내", """
                안녕하세요, PlanMate입니다.

                아이디를 확인하려면 아래 링크를 클릭해 이메일 인증을 진행해 주세요.

                %s

                본인이 요청하지 않은 메일이라면 이 메일을 무시해 주세요.
                """.formatted(recoveryUrl));
    }

    @Override
    public void sendPasswordReset(UserEntity user, String rawToken) {
        validateFromAddress();
        String resetUrl = frontendBaseUrl + "/auth/reset-password?token=" + rawToken;
        sendPlainText(user.getEmail(), "[PlanMate] 비밀번호 재설정 안내", """
                안녕하세요, PlanMate입니다.

                비밀번호를 재설정하려면 아래 링크를 클릭해 주세요.

                %s

                본인이 요청하지 않은 메일이라면 이 메일을 무시해 주세요.
                """.formatted(resetUrl));
    }

    private void sendPlainText(String to, String subject, String text) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);

            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new EmailSendFailedException(exception);
        }
    }

    private void validateFromAddress() {
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("app.mail.from must be configured.");
        }
    }

}
