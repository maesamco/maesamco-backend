package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailVerificationMailSender;
import com.maesamco.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증 메일 발송을 비동기로 처리합니다.
 *
 * <p>인증 요청 HTTP 응답 경로에서 기존 계정 여부 조회와 SMTP 발송을 분리하여,
 * 계정 존재 여부에 따른 응답 처리 시간 차이를 최소화합니다.</p>
 *
 * <p>이미 가입된 이메일에는 인증 메일을 발송하지 않습니다.
 * 이메일 원문, 인증 코드 및 메일 전송 예외 메시지는 로그에 기록하지 않습니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationMailDispatchService {

    private final UserRepository userRepository;

    private final EmailVerificationMailSender
            emailVerificationMailSender;

    /**
     * 이메일 인증 메일 발송 작업을 비동기로 수행합니다.
     *
     * <p>가입된 이메일이면 실제 메일을 발송하지 않고 종료합니다.
     * 미가입 이메일인 경우에만 SMTP Adapter에 발송을 위임합니다.</p>
     *
     * <p>메일 발송 실패는 HTTP 요청 결과에 영향을 주지 않도록
     * 비동기 작업 내부에서 처리합니다.</p>
     *
     * @param recipientEmail 정규화된 수신 이메일
     * @param emailLookupHash 이메일 조회용 해시
     * @param verificationCode 이메일 인증 코드 원문
     */
    @Async("emailVerificationTaskExecutor")
    public void dispatch(
            String recipientEmail,
            String emailLookupHash,
            String verificationCode
    ) {
        try {
            boolean existingUser =
                    userRepository.existsByEmailLookupHash(
                            emailLookupHash
                    );

            if (existingUser) {
                return;
            }

            emailVerificationMailSender.sendVerificationCode(
                    recipientEmail,
                    verificationCode
            );
        } catch (RuntimeException exception) {
            /*
             * SMTP 예외 메시지 등에 수신 이메일이 포함될 가능성이 있으므로
             * 예외 객체나 message 자체를 로그에 남기지 않습니다.
             */
            log.warn(
                    "이메일 인증 메일 비동기 발송에 실패했습니다. errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
