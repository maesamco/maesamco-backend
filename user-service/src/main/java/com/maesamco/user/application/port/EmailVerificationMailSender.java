package com.maesamco.user.application.port;

/**
 * 이메일 인증 코드를 사용자에게 전달하기 위한 Port입니다.
 *
 * <p>Application 계층은 SMTP, JavaMailSender 등의
 * 메일 전송 구현 세부사항을 알지 않습니다.
 * Infrastructure 계층에서 이 계약을 구현합니다.</p>
 *
 * <p>수신 이메일과 인증 코드는 민감정보이므로
 * 구현체에서 로그에 원문을 기록해서는 안 됩니다.</p>
 */
public interface EmailVerificationMailSender {

    /**
     * 이메일 인증 코드를 지정된 수신자에게 전송합니다.
     *
     * @param recipientEmail 인증 메일을 받을 이메일
     * @param verificationCode 사용자에게 전달할 인증 코드 원문
     */
    void sendVerificationCode(
            String recipientEmail,
            String verificationCode
    );
}
