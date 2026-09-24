package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.application.port.SocialSignupTokenStore;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.TokenExpirationCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 소셜 로그인에서 SIGNUP_REQUIRED를 받은 신규 사용자의 회원가입을 완료합니다(#308).
 *
 * <p>처리 순서는 일반 회원가입({@link SignUpService})과 같은 원칙을 따릅니다.</p>
 *
 * <ol>
 *     <li>socialSignupToken을 <b>소비하지 않고</b> 조회해 유효성과 Provider를 확인합니다.</li>
 *     <li>이미 연결된 소셜 계정 / 이미 가입된 이메일 / 닉네임 중복을 확인합니다.
 *     사용자가 닉네임만 바꿔 재시도할 수 있도록 이 단계까지는 Token을 남겨 둡니다.</li>
 *     <li>저장 직전 Token을 Redis에서 원자적으로 소비합니다.
 *     같은 Token으로 동시에 요청해도 하나의 요청만 가입 단계로 진입합니다.</li>
 *     <li>User와 SocialAccount를 하나의 DB 트랜잭션으로 저장합니다.</li>
 *     <li>JWT와 Redis 인증 세션을 발급합니다(자동 로그인).</li>
 * </ol>
 *
 * <p>Redis Token 소비와 DB 저장은 하나의 트랜잭션이 아니므로, 소비 이후 DB 저장이 실패하면
 * 사용자는 소셜 로그인부터 다시 진행해야 합니다. 소셜 로그인은 버튼 한 번으로 새 Token을 받을 수 있어
 * 일반 회원가입(이메일 인증 재진행)보다 복구 비용이 작습니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignUpService {

    private final EmailVerificationSecretHasher secretHasher;
    private final SocialSignupTokenStore socialSignupTokenStore;
    private final SignUpPersistenceService signUpPersistenceService;
    private final SocialSignUpPersistenceService socialSignUpPersistenceService;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final Clock clock;

    /**
     * 소셜 신규 사용자를 생성하고 자동 로그인용 인증 세션을 발급합니다.
     *
     * @param command 소셜 회원가입 입력값
     * @return 생성된 사용자 정보와 발급된 인증 토큰 정보
     */
    public SignUpResult signUp(
            SocialSignUpCommand command
    ) {
        Objects.requireNonNull(
                command,
                "소셜 회원가입 명령은 필수입니다."
        );

        String tokenHash =
                secretHasher.hashSignupToken(
                        command.socialSignupToken()
                );

        /*
         * 1. Token 사전 검증 (소비하지 않음)
         *
         * 유효한 Token을 가진 요청만 이후 중복 검사에 진입하므로,
         * 미인증 요청이 닉네임·계정 존재 여부를 정찰할 수 없습니다.
         */
        SocialSignupTicket ticket =
                socialSignupTokenStore
                        .find(tokenHash)
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID
                                )
                        );

        validateProvider(
                command,
                ticket
        );

        /*
         * 2. 가입 가능 여부 확인 (소비하지 않음)
         */
        socialSignUpPersistenceService.validateSignupAvailable(
                ticket
        );

        signUpPersistenceService.validateNicknameNotDuplicated(
                command.nickname()
        );

        /*
         * 입력값 검증(닉네임 형식, Java 경험 개월 수 등)은 Token 소비 전에 끝냅니다.
         * 잘못된 입력 때문에 Token이 소모되지 않도록 User 객체를 먼저 만듭니다.
         */
        User user =
                User.createSocial(
                        ticket.encryptedEmail(),
                        ticket.emailLookupHash(),
                        command.nickname(),
                        requireJavaExperienceMonths(command),
                        command.learningLevel()
                );

        /*
         * 3. Token 원자적 소비
         *
         * 사전 검증 이후 다른 요청이 같은 Token을 먼저 사용했거나 만료된 경우 여기서 실패합니다.
         */
        SocialSignupTicket consumedTicket =
                socialSignupTokenStore
                        .consume(tokenHash)
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID
                                )
                        );

        if (!consumedTicket.equals(ticket)) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID
            );
        }

        /*
         * 4. User + SocialAccount 원자적 저장
         *
         * 동일 Google 계정 / 동일 이메일 / 동일 닉네임 동시 가입 경쟁은
         * DB 부분 UNIQUE 인덱스가 최종적으로 막습니다.
         */
        User savedUser =
                socialSignUpPersistenceService.saveSocialUser(
                        user,
                        consumedTicket.provider(),
                        consumedTicket.providerUserId()
                );

        /*
         * 5. 자동 로그인
         */
        return issueSession(
                savedUser
        );
    }

    private void validateProvider(
            SocialSignUpCommand command,
            SocialSignupTicket ticket
    ) {
        /*
         * Google 가입 API에 다른 Provider로 발급된 Token을 사용하는 요청을 거부합니다.
         * 어떤 Provider의 Token이었는지는 응답으로 구분하지 않습니다.
         */
        if (ticket.provider() != command.provider()) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID
            );
        }
    }

    private int requireJavaExperienceMonths(
            SocialSignUpCommand command
    ) {
        if (command.javaExperienceMonths() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "Java 경험 개월 수는 필수입니다."
            );
        }

        return command.javaExperienceMonths();
    }

    private SignUpResult issueSession(
            User savedUser
    ) {
        UUID sessionId =
                UUID.randomUUID();

        UUID familyId =
                UUID.randomUUID();

        IssuedTokens issuedTokens =
                tokenIssuer.issueTokens(
                        savedUser.getId(),
                        savedUser.getRole(),
                        sessionId
                );

        Instant now =
                clock.instant();

        AuthSession authSession = new AuthSession(
                sessionId,
                familyId,
                savedUser.getId(),
                refreshTokenHasher.hash(
                        issuedTokens.refreshToken()
                ),
                now,
                issuedTokens.refreshTokenExpiresAt()
        );

        try {
            authSessionStore.save(
                    authSession
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "소셜 회원가입 완료 후 Redis 인증 세션 저장에 실패했습니다. "
                            + "userId={}, sessionId={}",
                    savedUser.getId(),
                    sessionId,
                    exception
            );

            throw new BusinessException(
                    ErrorCode.SIGNUP_AUTO_LOGIN_FAILED
            );
        }

        log.info(
                "소셜 회원가입이 완료되었습니다. userId={}",
                savedUser.getId()
        );

        long accessTokenExpiresIn =
                TokenExpirationCalculator.remainingSeconds(
                        now,
                        issuedTokens.accessTokenExpiresAt()
                );

        return new SignUpResult(
                savedUser.getId(),
                savedUser.getNickname(),
                savedUser.getRole(),
                savedUser.getStatus(),
                savedUser.getJavaExperienceMonths(),
                savedUser.getLearningLevel(),
                issuedTokens.accessToken(),
                accessTokenExpiresIn,
                issuedTokens
        );
    }
}
