package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailVerificationMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP를 이용해 이메일 인증 코드를 발송하는 Adapter입니다.
 *
 * <p>JavaMailSender 사용과 같은 메일 전송 구현 세부사항을
 * Infrastructure 계층에 한정합니다.</p>
 *
 * <p>수신 이메일과 인증 코드 원문은 로그에 기록하지 않습니다.</p>
 */
@Component
@RequiredArgsConstructor
public class SmtpEmailVerificationMailSender
        implements EmailVerificationMailSender {

    private static final String SUBJECT =
            "[매삼코] 이메일 인증 코드";

    private final JavaMailSender mailSender;

    @Override
    public void sendVerificationCode(
            String recipientEmail,
            String verificationCode
    ) {
        validateRequired(
                recipientEmail,
                "인증 메일 수신 이메일"
        );
        validateRequired(
                verificationCode,
                "이메일 인증 코드"
        );

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(recipientEmail);
        message.setSubject(SUBJECT);
        message.setText(
                """
                매삼코 이메일 인증 코드입니다.
                인증 코드: %s
                본인이 요청하지 않은 경우 이 메일을 무시해주세요.
                """.formatted(verificationCode)
        );

        mailSender.send(message);
    }

    private void validateRequired(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 필수입니다."
            );
        }
    }
}
