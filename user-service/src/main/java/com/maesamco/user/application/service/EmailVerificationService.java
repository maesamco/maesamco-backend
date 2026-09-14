package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.EmailVerificationSecretGenerator;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.EmailVerificationStore;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 이메일 인증 요청 흐름을 조율합니다.
 *
 * <p>이메일 원문을 정규화한 뒤 조회용 해시를 생성하고,
 * 인증 코드 원문 대신 HMAC 해시를 Redis challenge에 저장합니다.</p>
 *
 * <p>재전송 cooldown 또는 요청 횟수 제한에 걸린 경우에도
 * 외부에서 내부 상태를 구분할 수 없도록 예외를 발생시키지 않고 종료합니다.</p>
 *
 * <p>실제 계정 존재 여부 확인과 SMTP 발송은 비동기 발송 서비스에 위임하여
 * 인증 요청 HTTP 응답 경로와 분리합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailNormalizer emailNormalizer;
    private final EmailLookupHasher emailLookupHasher;

    private final EmailVerificationSecretGenerator
            emailVerificationSecretGenerator;

    private final EmailVerificationSecretHasher
            emailVerificationSecretHasher;

    private final EmailVerificationStore
            emailVerificationStore;

    private final EmailVerificationPolicy
            emailVerificationPolicy;

    private final EmailVerificationMailDispatchService
            emailVerificationMailDispatchService;

    /**
     * 이메일 인증 코드 발송을 요청합니다.
     *
     * <p>인증 코드는 안전한 난수로 생성한 뒤 HMAC 해시만 저장하며,
     * Redis challenge가 새로 생성된 경우에만 비동기 메일 발송을 요청합니다.</p>
     *
     * <p>cooldown 또는 요청 횟수 제한 상태는 호출자에게 노출하지 않습니다.</p>
     *
     * @param command 이메일 인증 요청 입력값
     */
    public void requestVerification(
            RequestEmailVerificationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "이메일 인증 요청 명령은 필수입니다."
        );

        String normalizedEmail =
                emailNormalizer.normalize(
                        command.email()
                );

        String emailLookupHash =
                emailLookupHasher.hash(
                        normalizedEmail
                );

        String verificationCode =
                emailVerificationSecretGenerator
                        .generateVerificationCode();

        String verificationCodeHash =
                emailVerificationSecretHasher
                        .hashVerificationCode(
                                verificationCode
                        );

        EmailVerificationStore.ChallengeCreationResult
                creationResult =
                emailVerificationStore.createChallenge(
                        emailLookupHash,
                        verificationCodeHash,
                        emailVerificationPolicy.challengeTtl(),
                        emailVerificationPolicy.resendCooldown(),
                        emailVerificationPolicy.requestLimitWindow(),
                        emailVerificationPolicy.maxRequestsPerWindow()
                );

        if (creationResult
                != EmailVerificationStore.ChallengeCreationResult.CREATED) {
            return;
        }

        try {
            emailVerificationMailDispatchService.dispatch(
                    normalizedEmail,
                    emailLookupHash,
                    verificationCode
            );
        } catch (RuntimeException exception) {
            /*
             * 비동기 Executor의 Queue가 포화되어 작업 등록 자체가
             * 거절되더라도 외부 인증 요청 응답 계약은 변경하지 않습니다.
             *
             * 이메일, 인증 코드 및 예외 메시지는 로그에 남기지 않습니다.
             */
            log.warn(
                    "이메일 인증 메일 비동기 작업 등록에 실패했습니다. errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
    /**
     * 이메일 인증 코드를 확인하고
     * 회원가입에 사용할 일회용 인증 토큰을 발급합니다.
     *
     * <p>이메일과 인증 코드는 각각 정규화 및 해시 처리한 뒤
     * Redis challenge와 원자적으로 비교합니다.</p>
     *
     * <p>인증에 성공하면 원문 회원가입 토큰은 호출자에게 반환하고,
     * Redis에는 해당 토큰의 HMAC 해시만 저장합니다.</p>
     *
     * @param command 이메일 인증 코드 확인 입력값
     * @return 일회용 회원가입 인증 토큰과 만료 시간
     * @throws BusinessException 인증 코드가 잘못되었거나 만료되었거나
     *                           최대 시도 횟수를 초과한 경우
     */
    public ConfirmEmailVerificationResult confirmVerification(
            ConfirmEmailVerificationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "이메일 인증 확인 명령은 필수입니다."
        );

        String normalizedEmail =
                emailNormalizer.normalize(
                        command.email()
                );

        String emailLookupHash =
                emailLookupHasher.hash(
                        normalizedEmail
                );

        String verificationCodeHash =
                emailVerificationSecretHasher
                        .hashVerificationCode(
                                command.verificationCode()
                        );

        String signupToken =
                emailVerificationSecretGenerator
                        .generateSignupToken();

        String signupTokenHash =
                emailVerificationSecretHasher
                        .hashSignupToken(
                                signupToken
                        );

        EmailVerificationStore.ConfirmationResult
                confirmationResult =
                emailVerificationStore
                        .confirmAndIssueSignupToken(
                                emailLookupHash,
                                verificationCodeHash,
                                signupTokenHash,
                                emailVerificationPolicy.signupTokenTtl(),
                                emailVerificationPolicy
                                        .maxVerificationAttempts()
                        );

        return switch (confirmationResult) {
            case VERIFIED ->
                    new ConfirmEmailVerificationResult(
                            signupToken,
                            emailVerificationPolicy
                                    .signupTokenTtl()
                                    .toSeconds()
                    );

            case INVALID_CODE ->
                    throw new BusinessException(
                            ErrorCode.EMAIL_VERIFICATION_INVALID_CODE
                    );

            case EXPIRED ->
                    throw new BusinessException(
                            ErrorCode.EMAIL_VERIFICATION_EXPIRED
                    );

            case ATTEMPTS_EXCEEDED ->
                    throw new BusinessException(
                            ErrorCode
                                    .EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED
                    );
        };
    }
}
