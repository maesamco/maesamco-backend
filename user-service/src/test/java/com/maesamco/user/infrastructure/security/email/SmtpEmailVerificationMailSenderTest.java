package com.maesamco.user.infrastructure.security.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SmtpEmailVerificationMailSender}의 단위 테스트입니다.
 *
 * <p>실제 SMTP 서버에는 연결하지 않고 {@link JavaMailSender}를 Mock으로 대체하여
 * 이메일 인증 메시지 생성 및 발송 요청만 검증합니다.</p>
 *
 * <p>테스트에서는 다음 동작을 확인합니다.</p>
 *
 * <ul>
 *     <li>정상적인 이메일과 인증 코드가 전달되면 메일을 발송합니다.</li>
 *     <li>수신 이메일이 비어 있으면 메일을 발송하지 않습니다.</li>
 *     <li>인증 코드가 비어 있으면 메일을 발송하지 않습니다.</li>
 * </ul>
 */
class SmtpEmailVerificationMailSenderTest {

    /**
     * 정상적인 수신 이메일과 인증 코드가 전달되면
     * 올바른 수신자, 제목, 인증 코드가 포함된 메일을 발송하는지 검증합니다.
     */
    @Test
    void sendsVerificationCodeEmail() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);

        SmtpEmailVerificationMailSender sender =
                new SmtpEmailVerificationMailSender(mailSender);

        String recipientEmail = "learner@example.com";
        String verificationCode = "123456";

        // when
        sender.sendVerificationCode(
                recipientEmail,
                verificationCode
        );

        // then
        // JavaMailSender.send(...)는 여러 오버로드가 존재하므로
        // SimpleMailMessage 타입의 ArgumentCaptor를 사용해 타입을 명확하게 지정합니다.
        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();

        // 수신 이메일이 요청한 이메일과 동일한지 검증합니다.
        assertArrayEquals(
                new String[]{recipientEmail},
                sentMessage.getTo()
        );

        // 인증 메일 제목이 의도한 제목인지 검증합니다.
        assertEquals(
                "[매삼코] 이메일 인증 코드",
                sentMessage.getSubject()
        );

        // 메일 본문에 실제 인증 코드가 포함되어 있는지 검증합니다.
        assertTrue(
                sentMessage.getText() != null
                        && sentMessage.getText().contains(verificationCode)
        );
    }

    /**
     * 수신 이메일이 공백이면 잘못된 요청으로 처리하고
     * 실제 메일 발송은 시도하지 않는지 검증합니다.
     */
    @Test
    void doesNotSendEmailWhenRecipientEmailIsBlank() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);

        SmtpEmailVerificationMailSender sender =
                new SmtpEmailVerificationMailSender(mailSender);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> sender.sendVerificationCode(
                        " ",
                        "123456"
                )
        );

        // 입력 검증에서 실패했으므로 메일 발송은 절대 호출되지 않아야 합니다.
        verify(mailSender, never())
                .send(any(SimpleMailMessage.class));
    }

    /**
     * 인증 코드가 공백이면 잘못된 요청으로 처리하고
     * 실제 메일 발송은 시도하지 않는지 검증합니다.
     */
    @Test
    void doesNotSendEmailWhenVerificationCodeIsBlank() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);

        SmtpEmailVerificationMailSender sender =
                new SmtpEmailVerificationMailSender(mailSender);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> sender.sendVerificationCode(
                        "learner@example.com",
                        " "
                )
        );

        // 인증 코드 검증에 실패했으므로 메일 발송은 발생하지 않아야 합니다.
        verify(mailSender, never())
                .send(any(SimpleMailMessage.class));
    }
}
