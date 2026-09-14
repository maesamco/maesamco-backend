package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailVerificationMailSender;
import com.maesamco.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EmailVerificationMailDispatchService}의
 * 이메일 인증 메일 발송 정책을 검증합니다.
 *
 * <p>실제 SMTP 서버에는 연결하지 않고
 * {@link UserRepository}와 {@link EmailVerificationMailSender}를 Mock으로 대체합니다.</p>
 */
@ExtendWith({
        MockitoExtension.class,
        OutputCaptureExtension.class
})
class EmailVerificationMailDispatchServiceTest {

    private static final String EMAIL =
            "learner@example.com";

    private static final String EMAIL_LOOKUP_HASH =
            "email-lookup-hash";

    private static final String VERIFICATION_CODE =
            "123456";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationMailSender
            emailVerificationMailSender;

    private EmailVerificationMailDispatchService
            emailVerificationMailDispatchService;

    /**
     * 각 테스트에서 동일한 Mock 의존성을 사용하는
     * 비동기 메일 발송 서비스를 생성합니다.
     *
     * <p>단위 테스트에서는 Spring Async Proxy를 사용하지 않으므로
     * {@code dispatch()} 메소드는 현재 테스트 스레드에서 직접 실행됩니다.</p>
     */
    @BeforeEach
    void setUp() {
        emailVerificationMailDispatchService =
                new EmailVerificationMailDispatchService(
                        userRepository,
                        emailVerificationMailSender
                );
    }

    /**
     * 아직 가입되지 않은 이메일이면
     * 실제 인증 메일 발송 Port를 호출합니다.
     */
    @Test
    @DisplayName("미가입 이메일이면 인증 메일을 발송한다")
    void dispatchesEmailForNonExistingUser() {
        // given
        when(
                userRepository.existsByEmailLookupHash(
                        EMAIL_LOOKUP_HASH
                )
        ).thenReturn(false);

        // when
        emailVerificationMailDispatchService.dispatch(
                EMAIL,
                EMAIL_LOOKUP_HASH,
                VERIFICATION_CODE
        );

        // then
        verify(emailVerificationMailSender)
                .sendVerificationCode(
                        EMAIL,
                        VERIFICATION_CODE
                );
    }

    /**
     * 이미 가입된 이메일이면 계정 열거 방지 정책에 따라
     * 외부 응답과 무관하게 실제 인증 메일은 발송하지 않습니다.
     */
    @Test
    @DisplayName("이미 가입된 이메일이면 인증 메일을 발송하지 않는다")
    void doesNotDispatchEmailForExistingUser() {
        // given
        when(
                userRepository.existsByEmailLookupHash(
                        EMAIL_LOOKUP_HASH
                )
        ).thenReturn(true);

        // when
        emailVerificationMailDispatchService.dispatch(
                EMAIL,
                EMAIL_LOOKUP_HASH,
                VERIFICATION_CODE
        );

        // then
        verifyNoInteractions(
                emailVerificationMailSender
        );
    }

    /**
     * 사용자 존재 여부 조회 중 장애가 발생해도
     * 비동기 작업의 예외를 호출자에게 전파하지 않습니다.
     */
    @Test
    @DisplayName("사용자 조회 실패를 호출자에게 전파하지 않는다")
    void doesNotPropagateUserLookupFailure() {
        // given
        when(
                userRepository.existsByEmailLookupHash(
                        EMAIL_LOOKUP_HASH
                )
        ).thenThrow(
                new RuntimeException(
                        "repository failure"
                )
        );

        // when & then
        assertThatCode(
                () -> emailVerificationMailDispatchService.dispatch(
                        EMAIL,
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE
                )
        ).doesNotThrowAnyException();

        verifyNoInteractions(
                emailVerificationMailSender
        );
    }

    /**
     * SMTP 발송 중 장애가 발생해도
     * 이메일 인증 요청 흐름으로 예외를 다시 전파하지 않습니다.
     */
    @Test
    @DisplayName("SMTP 발송 실패를 호출자에게 전파하지 않는다")
    void doesNotPropagateMailSenderFailure() {
        // given
        when(
                userRepository.existsByEmailLookupHash(
                        EMAIL_LOOKUP_HASH
                )
        ).thenReturn(false);

        doThrow(
                new RuntimeException(
                        "smtp failure"
                )
        ).when(
                emailVerificationMailSender
        ).sendVerificationCode(
                EMAIL,
                VERIFICATION_CODE
        );

        // when & then
        assertThatCode(
                () -> emailVerificationMailDispatchService.dispatch(
                        EMAIL,
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE
                )
        ).doesNotThrowAnyException();
    }

    /**
     * SMTP 예외 메시지에 민감정보가 포함되어 있어도
     * 이메일 원문과 인증 코드는 로그에 기록하지 않습니다.
     */
    @Test
    @DisplayName("SMTP 발송 실패 로그에 이메일과 인증 코드를 노출하지 않는다")
    void doesNotLogSensitiveValuesWhenMailSenderFails(
            CapturedOutput output
    ) {
        // given
        when(
                userRepository.existsByEmailLookupHash(
                        EMAIL_LOOKUP_HASH
                )
        ).thenReturn(false);

        doThrow(
                new RuntimeException(
                        "smtp failure email="
                                + EMAIL
                                + ", code="
                                + VERIFICATION_CODE
                )
        ).when(
                emailVerificationMailSender
        ).sendVerificationCode(
                EMAIL,
                VERIFICATION_CODE
        );

        // when
        emailVerificationMailDispatchService.dispatch(
                EMAIL,
                EMAIL_LOOKUP_HASH,
                VERIFICATION_CODE
        );

        // then
        assertThat(output.getAll())
                .contains(
                        "이메일 인증 메일 비동기 발송에 실패했습니다."
                )
                .contains(
                        "RuntimeException"
                )
                .doesNotContain(
                        EMAIL,
                        VERIFICATION_CODE
                );
    }
}
