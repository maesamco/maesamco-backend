package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.EmailVerificationStore;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 사용자 회원가입과 자동 로그인용 인증 세션 생성을 처리합니다.
 *
 * <p>이메일 보호, 비밀번호 해시, 사용자와 초기 게이미피케이션 상태 저장,
 * JWT 발급 및 Redis 인증 세션 저장을 하나의 회원가입 흐름으로 조율합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignUpService {

    private final EmailNormalizer emailNormalizer;
    private final EmailCipher emailCipher;
    private final EmailLookupHasher emailLookupHasher;
    private final PasswordHasher passwordHasher;
    private final SignUpPersistenceService signUpPersistenceService;
    private final AuthSessionIssuer authSessionIssuer;
    private final EmailVerificationSecretHasher emailVerificationSecretHasher;
    private final EmailVerificationStore emailVerificationStore;

    /**
     * 신규 사용자를 생성하고 자동 로그인용 인증 세션을 발급합니다.
     *
     * <p>사용자와 초기 게이미피케이션 상태 저장은
     * 별도의 DB 트랜잭션에서 처리하며,
     * JWT 발급과 Redis 인증 세션 저장은 해당 트랜잭션에 포함하지 않습니다.</p>
     *
     * <p>닉네임 존재 여부가 미인증 요청에 노출되지 않도록
     * signup token의 이메일 귀속과 유효성을 먼저 확인합니다.
     * 이 사전 검증 단계에서는 토큰을 소비하지 않습니다.</p>
     *
     * <p>사전 검증 이후 닉네임 중복 검사를 수행하고,
     * 실제 회원 생성 직전 signup token을 다시 원자적으로 소비합니다.
     * 따라서 사전 검증과 최종 소비 사이에 다른 요청이 같은 토큰을 사용하더라도
     * 하나의 요청만 회원 생성 단계로 진입할 수 있습니다.</p>
     *
     * <p>Redis의 인증 토큰 소비와 DB 회원 저장은 하나의 트랜잭션으로 묶이지 않습니다.
     * 따라서 최종 토큰 소비 이후 닉네임 동시 가입 경쟁이나 DB 제약 위반 등으로
     * 회원 저장이 실패하더라도 이미 소비된 토큰은 복구하지 않습니다.
     * 현재 정책에서는 이런 경우 이메일 인증부터 다시 진행해야 합니다.</p>
     *
     * @param command 회원가입 입력값
     * @return 생성된 사용자 정보와 발급된 인증 토큰 정보
     */
    public SignUpResult signUp(SignUpCommand command) {
        Objects.requireNonNull(
                command,
                "회원가입 명령은 필수입니다."
        );

        String normalizedEmail =
                emailNormalizer.normalize(
                        command.email()
                );

        String emailLookupHash =
                emailLookupHasher.hash(
                        normalizedEmail
                );

        String normalizedNickname =
                normalizeNickname(
                        command.nickname()
                );

        String signupTokenHash =
                emailVerificationSecretHasher.hashSignupToken(
                        command.signupToken()
                );

        /*
         * 닉네임 중복 조회 전에 이메일 인증이 완료된 요청인지 먼저 확인합니다.
         *
         * 이 단계에서는 토큰을 삭제하지 않습니다.
         * 따라서 인증된 사용자가 단순 닉네임 중복 때문에
         * 이메일 인증부터 다시 수행하는 문제를 방지합니다.
         */
        boolean verificationTokenValid =
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        if (!verificationTokenValid) {
            throw new BusinessException(
                    ErrorCode.SIGNUP_VERIFICATION_TOKEN_INVALID
            );
        }

        /*
         * signup token 사전 검증을 통과한 요청에 대해서만
         * 닉네임 존재 여부를 조회합니다.
         *
         * 이를 통해 미인증 요청이 응답 차이를 이용해
         * 임의 닉네임의 사용 여부를 확인하는 것을 방지합니다.
         */
        signUpPersistenceService.validateNicknameNotDuplicated(
                normalizedNickname
        );

        /*
         * 실제 회원 생성 직전에 signup token을 원자적으로 소비합니다.
         *
         * 사전 검증 이후 다른 요청이 동일 토큰을 먼저 사용했거나
         * 그 사이 토큰이 만료된 경우 여기서 실패합니다.
         *
         * 비교와 삭제는 Redis에서 하나의 원자 연산으로 수행되므로
         * 동일 토큰을 이용한 동시 회원가입 및 replay를 차단합니다.
         */
        boolean verificationTokenConsumed =
                emailVerificationStore.consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                );

        if (!verificationTokenConsumed) {
            throw new BusinessException(
                    ErrorCode.SIGNUP_VERIFICATION_TOKEN_INVALID
            );
        }

        String encryptedEmail =
                emailCipher.encrypt(
                        normalizedEmail
                );

        String passwordHash =
                passwordHasher.hash(
                        command.password()
                );

        User user = User.create(
                encryptedEmail,
                emailLookupHash,
                passwordHash,
                normalizedNickname,
                command.javaExperienceMonths(),
                command.learningLevel()
        );

        User savedUser =
                signUpPersistenceService.saveUser(
                        user
                );

        IssuedAuthSession authSession =
                authSessionIssuer.issue(
                        savedUser,
                        AuthSessionPurpose.SIGNUP
                );

        log.info(
                "회원가입이 완료되었습니다. userId={}",
                savedUser.getId()
        );

        return new SignUpResult(
                savedUser.getId(),
                savedUser.getNickname(),
                savedUser.getRole(),
                savedUser.getStatus(),
                savedUser.getJavaExperienceMonths(),
                savedUser.getLearningLevel(),
                authSession.issuedTokens().accessToken(),
                authSession.accessTokenExpiresIn(),
                authSession.issuedTokens()
        );
    }

    /**
     * 중복 검사와 저장에 사용할 닉네임의 앞뒤 공백을 제거합니다.
     *
     * <p>필수값과 길이 검증은 {@link User#create}에서 수행합니다.</p>
     */
    private String normalizeNickname(
            String nickname
    ) {
        if (nickname == null) {
            return null;
        }

        return nickname.trim();
    }
}
