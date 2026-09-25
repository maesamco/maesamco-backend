package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailVerificationMailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * SMTP를 이용해 이메일 인증 코드를 발송하는 Adapter입니다.
 *
 * <p>JavaMailSender 사용과 같은 메일 전송 구현 세부사항을
 * Infrastructure 계층에 한정합니다.</p>
 *
 * <p>HTML 메일(브랜드 로고·마스코트 포함)과 텍스트 대체본을 함께 보냅니다. 이미지는 외부 URL이 아니라
 * 메일에 인라인(CID)으로 담아서, 외부 이미지 차단 설정과 무관하게 보이고 별도 이미지 호스팅이 필요 없습니다.
 * 라이트/다크 모드는 {@code prefers-color-scheme}로 대응하며 다크용 로고는 미디어 쿼리로 바꿔 보여줍니다
 * (템플릿: {@code resources/mail/email-verification.html}).</p>
 *
 * <p>수신 이메일과 인증 코드 원문은 로그에 기록하지 않습니다.</p>
 */
@Component
public class SmtpEmailVerificationMailSender
        implements EmailVerificationMailSender {

    private static final String SUBJECT =
            "[매삼코] 이메일 인증 코드";

    private static final String TEMPLATE_PATH = "mail/email-verification.html";
    private static final String CODE_PLACEHOLDER = "{{CODE}}";

    private static final String PLAIN_TEXT_TEMPLATE =
            """
            매삼코 이메일 인증 코드입니다.

            인증 코드: %s

            인증 코드를 다시 요청한 경우
            이전에 발급된 인증 코드는 즉시 사용할 수 없습니다.
            가장 최근에 받은 인증 코드를 입력해주세요.

            본인이 요청하지 않은 경우 이 메일을 무시해주세요.
            """;

    private final JavaMailSender mailSender;
    private final String htmlTemplate;

    public SmtpEmailVerificationMailSender(
            JavaMailSender mailSender
    ) {
        this.mailSender = mailSender;
        this.htmlTemplate = loadTemplate();
    }

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

        try {
            MimeMessage message = mailSender.createMimeMessage();

            // multipart(related): HTML 본문과 인라인 이미지(CID)를 하나로 묶는다.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setTo(recipientEmail);
            helper.setSubject(SUBJECT);
            // 두 번째 인자가 HTML, 첫 번째 인자는 HTML을 열지 못하는 클라이언트를 위한 텍스트 대체본이다.
            helper.setText(
                    PLAIN_TEXT_TEMPLATE.formatted(verificationCode),
                    htmlTemplate.replace(
                            CODE_PLACEHOLDER,
                            HtmlUtils.htmlEscape(verificationCode)
                    )
            );

            helper.addInline("logo", new ClassPathResource("mail/logo.png"), "image/png");
            helper.addInline("logo-dark", new ClassPathResource("mail/logo-dark.png"), "image/png");
            helper.addInline("mascot", new ClassPathResource("mail/mascot.png"), "image/png");

            mailSender.send(message);
        } catch (MessagingException e) {
            // MailException 계열로 바꿔 던져, 기존 SimpleMailMessage 발송 실패와 같은 방식으로 처리되게 한다.
            throw new org.springframework.mail.MailPreparationException(
                    "이메일 인증 메일을 만들지 못했습니다.",
                    e
            );
        }
    }

    private static String loadTemplate() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(TEMPLATE_PATH).getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "이메일 인증 메일 템플릿을 읽지 못했습니다: " + TEMPLATE_PATH,
                    e
            );
        }
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
