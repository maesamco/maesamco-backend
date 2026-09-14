package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.EmailVerificationSecretGenerator;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.EmailVerificationStore;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * {@link EmailVerificationService}의 이메일 인증 요청 흐름을 검증합니다.
 *
 * <p>이 테스트에서는 실제 Redis, SMTP 및 난수 생성기를 사용하지 않고
 * Application Service가 각 Port와 비동기 발송 서비스를
 * 올바른 순서와 값으로 호출하는지 검증합니다.</p>
 */
@ExtendWith({
        MockitoExtension.class,
        OutputCaptureExtension.class
})
class EmailVerificationServiceTest {

    private static final String RAW_EMAIL =
            "  Learner@Example.com  ";

    private static final String NORMALIZED_EMAIL =
            "learner@example.com";

    private static final String EMAIL_LOOKUP_HASH =
            "email-lookup-hash";

    private static final String VERIFICATION_CODE =
            "123456";

    private static final String VERIFICATION_CODE_HASH =
            "verification-code-hash";

    private static final String SIGNUP_TOKEN =
            "generated-signup-token";

    private static final String SIGNUP_TOKEN_HASH =
            "signup-token-hash";

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private EmailVerificationSecretGenerator
            emailVerificationSecretGenerator;

    @Mock
    private EmailVerificationSecretHasher
            emailVerificationSecretHasher;

    @Mock
    private EmailVerificationStore emailVerificationStore;

    @Mock
    private EmailVerificationMailDispatchService
            emailVerificationMailDispatchService;

    private EmailVerificationPolicy emailVerificationPolicy;

    private EmailVerificationService emailVerificationService;

    /**
     * 각 테스트에서 동일한 인증 정책과 Mock 의존성을 사용하도록
     * Service를 새로 구성합니다.
     */
    @BeforeEach
    void setUp() {
        emailVerificationPolicy =
                new EmailVerificationPolicy(
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(1),
                        Duration.ofHours(1),
                        5,
                        5
                );

        emailVerificationService =
                new EmailVerificationService(
                        emailNormalizer,
                        emailLookupHasher,
                        emailVerificationSecretGenerator,
                        emailVerificationSecretHasher,
                        emailVerificationStore,
                        emailVerificationPolicy,
                        emailVerificationMailDispatchService
                );
    }

    /**
     * Redis challenge가 정상 생성되면
     * 정규화된 이메일과 인증 코드를 비동기 발송 서비스에 전달합니다.
     */
    @Test
    @DisplayName("인증 challenge가 생성되면 인증 메일 발송을 요청한다")
    void dispatchesVerificationEmailWhenChallengeCreated() {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        RAW_EMAIL
                );

        stubVerificationPreparation();

        when(
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        emailVerificationPolicy.challengeTtl(),
                        emailVerificationPolicy.resendCooldown(),
                        emailVerificationPolicy.requestLimitWindow(),
                        emailVerificationPolicy.maxRequestsPerWindow()
                )
        ).thenReturn(
                EmailVerificationStore.ChallengeCreationResult.CREATED
        );

        // when
        emailVerificationService.requestVerification(
                command
        );

        // then
        verify(emailNormalizer)
                .normalize(command.email());

        verify(emailLookupHasher)
                .hash(NORMALIZED_EMAIL);

        verify(emailVerificationSecretGenerator)
                .generateVerificationCode();

        verify(emailVerificationSecretHasher)
                .hashVerificationCode(
                        VERIFICATION_CODE
                );

        verify(emailVerificationStore)
                .createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        emailVerificationPolicy.challengeTtl(),
                        emailVerificationPolicy.resendCooldown(),
                        emailVerificationPolicy.requestLimitWindow(),
                        emailVerificationPolicy.maxRequestsPerWindow()
                );

        verify(emailVerificationMailDispatchService)
                .dispatch(
                        NORMALIZED_EMAIL,
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE
                );
    }

    /**
     * 재전송 cooldown이 활성화된 경우
     * 새로운 메일 발송 작업을 등록하지 않습니다.
     */
    @Test
    @DisplayName("재전송 cooldown이 활성화되면 메일 발송을 요청하지 않는다")
    void doesNotDispatchEmailWhenCooldownActive() {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        RAW_EMAIL
                );

        stubVerificationPreparation();

        when(
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        emailVerificationPolicy.challengeTtl(),
                        emailVerificationPolicy.resendCooldown(),
                        emailVerificationPolicy.requestLimitWindow(),
                        emailVerificationPolicy.maxRequestsPerWindow()
                )
        ).thenReturn(
                EmailVerificationStore.ChallengeCreationResult
                        .COOLDOWN_ACTIVE
        );

        // when
        emailVerificationService.requestVerification(
                command
        );

        // then
        verifyNoInteractions(
                emailVerificationMailDispatchService
        );
    }

    /**
     * 요청 횟수 제한을 초과한 경우에도
     * 외부에서 내부 제한 상태를 구분할 수 없도록 예외를 발생시키지 않고
     * 메일 발송도 요청하지 않습니다.
     */
    @Test
    @DisplayName("요청 횟수 제한을 초과하면 메일 발송을 요청하지 않는다")
    void doesNotDispatchEmailWhenRateLimitExceeded() {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        RAW_EMAIL
                );

        stubVerificationPreparation();

        when(
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        emailVerificationPolicy.challengeTtl(),
                        emailVerificationPolicy.resendCooldown(),
                        emailVerificationPolicy.requestLimitWindow(),
                        emailVerificationPolicy.maxRequestsPerWindow()
                )
        ).thenReturn(
                EmailVerificationStore.ChallengeCreationResult
                        .RATE_LIMIT_EXCEEDED
        );

        // when
        emailVerificationService.requestVerification(
                command
        );

        // then
        verifyNoInteractions(
                emailVerificationMailDispatchService
        );
    }

    /**
     * 비동기 Executor가 포화되어 발송 작업 등록 자체가 실패하더라도
     * 인증 요청 흐름의 외부 계약을 깨뜨리지 않도록 예외를 전파하지 않습니다.
     */
    @Test
    @DisplayName("비동기 메일 작업 등록 실패를 외부로 전파하지 않는다")
    void doesNotPropagateMailDispatchFailure() {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        RAW_EMAIL
                );

        stubVerificationPreparation();

        when(
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        emailVerificationPolicy.challengeTtl(),
                        emailVerificationPolicy.resendCooldown(),
                        emailVerificationPolicy.requestLimitWindow(),
                        emailVerificationPolicy.maxRequestsPerWindow()
                )
        ).thenReturn(
                EmailVerificationStore.ChallengeCreationResult.CREATED
        );

        doThrow(
                new RuntimeException(
                        "async task rejected"
                )
        ).when(
                emailVerificationMailDispatchService
        ).dispatch(
                NORMALIZED_EMAIL,
                EMAIL_LOOKUP_HASH,
                VERIFICATION_CODE
        );

        // when & then
        assertThatCode(
                () -> emailVerificationService.requestVerification(
                        command
                )
        ).doesNotThrowAnyException();
    }

    /**
     * 비동기 메일 작업 등록 예외에 민감정보가 포함되어 있어도
     * 이메일 원문과 인증 코드는 로그에 기록하지 않습니다.
     */
    @Test
    @DisplayName("비동기 메일 작업 등록 실패 로그에 이메일과 인증 코드를 노출하지 않는다")
    void doesNotLogSensitiveValuesWhenMailDispatchFails(
            CapturedOutput output
    ) {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        RAW_EMAIL
                );

        stubVerificationPreparation();

        when(
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        emailVerificationPolicy.challengeTtl(),
                        emailVerificationPolicy.resendCooldown(),
                        emailVerificationPolicy.requestLimitWindow(),
                        emailVerificationPolicy.maxRequestsPerWindow()
                )
        ).thenReturn(
                EmailVerificationStore.ChallengeCreationResult.CREATED
        );

        doThrow(
                new RuntimeException(
                        "async task rejected email="
                                + NORMALIZED_EMAIL
                                + ", code="
                                + VERIFICATION_CODE
                )
        ).when(
                emailVerificationMailDispatchService
        ).dispatch(
                NORMALIZED_EMAIL,
                EMAIL_LOOKUP_HASH,
                VERIFICATION_CODE
        );

        // when
        emailVerificationService.requestVerification(
                command
        );

        // then
        assertThat(output.getAll())
                .contains(
                        "이메일 인증 메일 비동기 작업 등록에 실패했습니다."
                )
                .contains(
                        "RuntimeException"
                )
                .doesNotContain(
                        NORMALIZED_EMAIL,
                        VERIFICATION_CODE
                );
    }

    /**
     * 올바른 인증 코드이면 Redis challenge를 확인하고
     * 회원가입에 사용할 일회용 인증 토큰을 반환합니다.
     */
    @Test
    @DisplayName("올바른 인증 코드이면 회원가입 인증 토큰을 발급한다")
    void issuesSignupTokenWhenVerificationSucceeds() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        RAW_EMAIL,
                        VERIFICATION_CODE
                );

        stubConfirmationPreparation();

        when(
                emailVerificationStore.confirmAndIssueSignupToken(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        SIGNUP_TOKEN_HASH,
                        emailVerificationPolicy.signupTokenTtl(),
                        emailVerificationPolicy.maxVerificationAttempts()
                )
        ).thenReturn(
                EmailVerificationStore.ConfirmationResult.VERIFIED
        );

        // when
        ConfirmEmailVerificationResult result =
                emailVerificationService.confirmVerification(
                        command
                );

        // then
        assertThat(result.signupToken())
                .isEqualTo(SIGNUP_TOKEN);

        assertThat(result.expiresInSeconds())
                .isEqualTo(600L);

        verify(emailVerificationStore)
                .confirmAndIssueSignupToken(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        SIGNUP_TOKEN_HASH,
                        emailVerificationPolicy.signupTokenTtl(),
                        emailVerificationPolicy.maxVerificationAttempts()
                );
    }

    /**
     * 인증 코드가 일치하지 않으면
     * 잘못된 인증 코드 오류를 반환합니다.
     */
    @Test
    @DisplayName("인증 코드가 올바르지 않으면 잘못된 인증 코드 오류를 반환한다")
    void throwsInvalidCodeWhenVerificationCodeDoesNotMatch() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        RAW_EMAIL,
                        VERIFICATION_CODE
                );

        stubConfirmationPreparation();

        when(
                emailVerificationStore.confirmAndIssueSignupToken(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        SIGNUP_TOKEN_HASH,
                        emailVerificationPolicy.signupTokenTtl(),
                        emailVerificationPolicy.maxVerificationAttempts()
                )
        ).thenReturn(
                EmailVerificationStore.ConfirmationResult.INVALID_CODE
        );

        // when
        BusinessException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BusinessException.class,
                        () -> emailVerificationService
                                .confirmVerification(command)
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode.EMAIL_VERIFICATION_INVALID_CODE
                );
    }

    /**
     * 인증 challenge가 만료된 경우
     * 만료 오류를 반환합니다.
     */
    @Test
    @DisplayName("인증 코드가 만료되면 만료 오류를 반환한다")
    void throwsExpiredWhenVerificationExpired() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        RAW_EMAIL,
                        VERIFICATION_CODE
                );

        stubConfirmationPreparation();

        when(
                emailVerificationStore.confirmAndIssueSignupToken(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        SIGNUP_TOKEN_HASH,
                        emailVerificationPolicy.signupTokenTtl(),
                        emailVerificationPolicy.maxVerificationAttempts()
                )
        ).thenReturn(
                EmailVerificationStore.ConfirmationResult.EXPIRED
        );

        // when
        BusinessException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BusinessException.class,
                        () -> emailVerificationService
                                .confirmVerification(command)
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode.EMAIL_VERIFICATION_EXPIRED
                );
    }

    /**
     * 허용된 인증 시도 횟수를 모두 사용한 경우
     * 시도 횟수 초과 오류를 반환합니다.
     */
    @Test
    @DisplayName("인증 시도 횟수를 초과하면 시도 횟수 초과 오류를 반환한다")
    void throwsAttemptsExceededWhenMaximumAttemptsReached() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        RAW_EMAIL,
                        VERIFICATION_CODE
                );

        stubConfirmationPreparation();

        when(
                emailVerificationStore.confirmAndIssueSignupToken(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        SIGNUP_TOKEN_HASH,
                        emailVerificationPolicy.signupTokenTtl(),
                        emailVerificationPolicy.maxVerificationAttempts()
                )
        ).thenReturn(
                EmailVerificationStore.ConfirmationResult
                        .ATTEMPTS_EXCEEDED
        );

        // when
        BusinessException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BusinessException.class,
                        () -> emailVerificationService
                                .confirmVerification(command)
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED
                );
    }

    /**
     * 이메일 인증 코드 확인에 공통으로 필요한
     * 이메일 정규화, 조회용 해시, 인증 코드 해시,
     * 회원가입 토큰 및 토큰 해시 생성을 설정합니다.
     */
    private void stubConfirmationPreparation() {
        when(
                emailNormalizer.normalize(
                        "Learner@Example.com"
                )
        ).thenReturn(
                NORMALIZED_EMAIL
        );

        when(
                emailLookupHasher.hash(
                        NORMALIZED_EMAIL
                )
        ).thenReturn(
                EMAIL_LOOKUP_HASH
        );

        when(
                emailVerificationSecretHasher
                        .hashVerificationCode(
                                VERIFICATION_CODE
                        )
        ).thenReturn(
                VERIFICATION_CODE_HASH
        );

        when(
                emailVerificationSecretGenerator
                        .generateSignupToken()
        ).thenReturn(
                SIGNUP_TOKEN
        );

        when(
                emailVerificationSecretHasher
                        .hashSignupToken(
                                SIGNUP_TOKEN
                        )
        ).thenReturn(
                SIGNUP_TOKEN_HASH
        );
    }

    /**
     * 이메일 인증 요청에 공통으로 필요한
     * 정규화, 조회용 해시, 인증 코드 및 인증 코드 해시 생성을 설정합니다.
     */
    private void stubVerificationPreparation() {
        when(
                emailNormalizer.normalize(
                        "Learner@Example.com"
                )
        ).thenReturn(
                NORMALIZED_EMAIL
        );

        when(
                emailLookupHasher.hash(
                        NORMALIZED_EMAIL
                )
        ).thenReturn(
                EMAIL_LOOKUP_HASH
        );

        when(
                emailVerificationSecretGenerator
                        .generateVerificationCode()
        ).thenReturn(
                VERIFICATION_CODE
        );

        when(
                emailVerificationSecretHasher
                        .hashVerificationCode(
                                VERIFICATION_CODE
                        )
        ).thenReturn(
                VERIFICATION_CODE_HASH
        );
    }
}
