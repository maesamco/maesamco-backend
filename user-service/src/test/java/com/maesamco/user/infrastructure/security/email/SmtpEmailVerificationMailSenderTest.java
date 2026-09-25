package com.maesamco.user.infrastructure.security.email;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SmtpEmailVerificationMailSender}의 단위 테스트입니다.
 *
 * <p>실제 SMTP 서버에는 연결하지 않고 {@link JavaMailSender}를 Mock으로 대체하여
 * 만들어진 MIME 메시지(HTML 본문, 텍스트 대체본, 인라인 이미지)와 발송 요청만 검증합니다.</p>
 */
class SmtpEmailVerificationMailSenderTest {

    private JavaMailSender mailSender;
    private SmtpEmailVerificationMailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        sender = new SmtpEmailVerificationMailSender(mailSender);
    }

    private MimeMessage sendAndCapture(String recipient, String code) throws Exception {
        sender.sendVerificationCode(recipient, code);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return message;
    }

    private static void collectParts(Part part, List<Part> parts) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collectParts(child, parts);
            }
        } else {
            parts.add(part);
        }
    }

    private static List<Part> leafParts(MimeMessage message) throws Exception {
        List<Part> parts = new ArrayList<>();
        collectParts(message, parts);
        return parts;
    }

    private static String textOf(List<Part> parts, String mimeType) throws Exception {
        for (Part part : parts) {
            if (part.isMimeType(mimeType)) {
                return (String) part.getContent();
            }
        }
        throw new AssertionError(mimeType + " 파트가 없습니다.");
    }

    @Test
    @DisplayName("올바른 수신자와 제목으로 인증 코드 메일을 발송한다")
    void sendsVerificationCodeEmail() throws Exception {
        MimeMessage message = sendAndCapture("learner@example.com", "123456");

        assertThat(message.getRecipients(Message.RecipientType.TO)).hasSize(1);
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("learner@example.com");
        assertThat(message.getSubject()).isEqualTo("[매삼코] 이메일 인증 코드");
    }

    @Test
    @DisplayName("HTML 본문에 인증 코드와 재발급 안내가 들어가고, 템플릿 자리표시자는 남지 않는다")
    void htmlBodyContainsCodeAndNotice() throws Exception {
        List<Part> parts = leafParts(sendAndCapture("learner@example.com", "123456"));

        String html = textOf(parts, "text/html");

        assertThat(html).contains("123456");
        assertThat(html).doesNotContain("{{CODE}}");
        assertThat(html).contains("이전에 받은 코드는 바로 사용할 수 없어요");
        assertThat(html).contains("본인이 요청하지 않았다면");
    }

    @Test
    @DisplayName("HTML을 열지 못하는 클라이언트를 위해 인증 코드가 들어간 텍스트 대체본도 함께 보낸다")
    void includesPlainTextAlternative() throws Exception {
        List<Part> parts = leafParts(sendAndCapture("learner@example.com", "123456"));

        String plain = textOf(parts, "text/plain");

        assertThat(plain).contains("123456");
        assertThat(plain).contains("이전에 발급된 인증 코드는 즉시 사용할 수 없습니다.");
    }

    @Test
    @DisplayName("로고(라이트/다크)와 마스코트를 외부 URL이 아니라 인라인(CID) 이미지로 담는다")
    void embedsBrandImagesAsInlineParts() throws Exception {
        List<Part> parts = leafParts(sendAndCapture("learner@example.com", "123456"));
        String html = textOf(parts, "text/html");

        List<String> contentIds = new ArrayList<>();
        for (Part part : parts) {
            if (part.isMimeType("image/png")) {
                String[] header = part.getHeader("Content-ID");
                assertThat(header).isNotNull();
                contentIds.add(header[0]);
                // 크기는 직렬화 전에는 알 수 없어(-1) 실제 내용을 읽어 PNG 시그니처를 확인한다.
                try (var in = part.getInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    assertThat(bytes).hasSizeGreaterThan(100);
                    assertThat(bytes[1]).isEqualTo((byte) 'P');
                    assertThat(bytes[2]).isEqualTo((byte) 'N');
                    assertThat(bytes[3]).isEqualTo((byte) 'G');
                }
            }
        }

        assertThat(contentIds).hasSize(3);
        assertThat(contentIds).anyMatch(id -> id.contains("logo-dark")); // 다크 모드용 로고
        assertThat(contentIds).anyMatch(id -> id.contains("mascot"));
        assertThat(html).contains("cid:logo\"", "cid:logo-dark\"", "cid:mascot\"");
        assertThat(html).doesNotContain("src=\"http"); // 외부 이미지 호스팅에 의존하지 않는다
    }

    @Test
    @DisplayName("라이트/다크 모드를 모두 선언하고 다크용 로고로 바꿔 보여준다")
    void declaresLightAndDarkColorSchemes() throws Exception {
        String html = textOf(leafParts(sendAndCapture("learner@example.com", "123456")), "text/html");

        assertThat(html).contains("<meta name=\"color-scheme\" content=\"light dark\">");
        assertThat(html).contains("@media (prefers-color-scheme: dark)");
        assertThat(html).contains(".logo-dark { display: block !important");
        assertThat(html).contains(".logo-light { display: none !important");
    }

    @Test
    @DisplayName("인증 코드는 HTML로 이스케이프해서 넣는다")
    void escapesVerificationCodeInHtml() throws Exception {
        String html = textOf(leafParts(sendAndCapture("learner@example.com", "<b>1&2</b>")), "text/html");

        assertThat(html).contains("&lt;b&gt;1&amp;2&lt;/b&gt;");
        assertThat(html).doesNotContain("<b>1&2</b>");
    }

    @Test
    @DisplayName("수신 이메일이 공백이면 메일을 만들거나 발송하지 않는다")
    void doesNotSendEmailWhenRecipientEmailIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> sender.sendVerificationCode(" ", "123456"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("인증 코드가 공백이면 메일을 만들거나 발송하지 않는다")
    void doesNotSendEmailWhenVerificationCodeIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> sender.sendVerificationCode("learner@example.com", " "));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
