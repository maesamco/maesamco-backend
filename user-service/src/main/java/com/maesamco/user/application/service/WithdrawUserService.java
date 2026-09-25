package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 회원 탈퇴를 처리합니다.
 *
 * <p>본인 확인 뒤 사용자, 관심 개념, 소셜 계정 연결을 논리 삭제하고,
 * 기존에 발급된 모든 인증 세션을 무효화합니다.</p>
 *
 * <p>본인 확인 수단은 계정 종류에 따라 다릅니다(#328).</p>
 * <ul>
 *     <li>비밀번호가 있는 계정: 현재 비밀번호</li>
 *     <li>비밀번호 없이 소셜로 가입한 계정: Google 재인증(ID Token의 sub가 연결된 SocialAccount와 일치)</li>
 * </ul>
 *
 * <p>소셜 계정 연결도 논리 삭제하므로, 탈퇴 후 같은 Google 계정으로 다시 가입할 수 있습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class WithdrawUserService {

    private final UserRepository userRepository;
    private final UserInterestConceptRepository
            interestConceptRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordHasher passwordHasher;
    private final SocialReauthenticator socialReauthenticator;
    private final AuthSessionLogoutAllStore
            authSessionLogoutAllStore;
    private final Clock clock;

    /**
     * 인증된 사용자를 탈퇴 처리합니다.
     *
     * <p>사용자 행에 비관적 쓰기 잠금을 적용하여 관심 개념 변경과
     * 다른 탈퇴 요청을 직렬화합니다. 프로필·비밀번호 등 User 본체 수정은
     * User의 낙관적 락 버전으로 충돌을 감지하여
     * 탈퇴 결과가 덮어써지지 않도록 합니다.</p>
     *
     * <p>DB 변경을 먼저 flush한 뒤 인증 세션을 무효화합니다.
     * 세션 무효화에 실패하면 예외가 전파되어 DB 변경도 롤백됩니다.</p>
     *
     * @param userId 인증된 사용자 식별자
     * @param command 회원 탈퇴 입력값
     */
    @Transactional
    public void withdraw(
            UUID userId,
            WithdrawUserCommand command
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                command,
                "회원 탈퇴 명령은 필수입니다."
        );

        /*
         * Google 재인증은 외부 Provider 검증을 포함하므로
         * 사용자 행 잠금을 잡기 전에 수행합니다(UpdateMyInterestsService와 같은 원칙).
         * 재인증은 ID Token의 sub가 이 사용자의 SocialAccount인지만 확인하므로 User 조회가 필요 없습니다.
         */
        if (command.usesGoogleReauth()) {
            socialReauthenticator.verifyOwnership(
                    userId,
                    SocialProvider.GOOGLE,
                    command.googleIdToken()
            );
        }

        User user =
                userRepository.findByIdForUpdate(
                        userId
                ).orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        user.assertActive();

        if (!command.usesGoogleReauth()) {
            /*
             * 소셜 계정으로만 가입한 사용자는 비밀번호가 없으므로
             * Google 재인증으로 탈퇴해야 합니다(#328).
             */
            if (!user.hasPassword()) {
                throw new BusinessException(
                        ErrorCode.USER_PASSWORD_NOT_SET
                );
            }

            validateCurrentPassword(
                    command.currentPassword(),
                    user.getPasswordHash()
            );
        }

        Instant withdrawnAt =
                clock.instant();

        interestConceptRepository.softDeleteAllByUserId(
                userId,
                userId,
                withdrawnAt
        );

        /*
         * 소셜 계정 연결을 함께 논리 삭제합니다.
         * 활성 행만 대상으로 하는 부분 UNIQUE 인덱스 덕분에 같은 Google 계정으로 재가입할 수 있습니다.
         */
        socialAccountRepository.softDeleteAllByUserId(
                userId,
                userId,
                withdrawnAt
        );

        user.softDelete(
                userId,
                withdrawnAt
        );

        /*
         * Redis를 갱신하기 전에 DB 오류를 확인할 수 있도록
         * saveAndFlush를 사용하는 Repository를 호출합니다.
         */
        userRepository.save(
                user
        );

        authSessionLogoutAllStore.logoutAll(
                userId,
                withdrawnAt
        );
    }


    /**
     * 입력한 현재 비밀번호와 저장된 비밀번호 해시가 일치하는지 확인합니다.
     */
    private void validateCurrentPassword(
            String currentPassword,
            String passwordHash
    ) {
        if (!passwordHasher.matches(
                currentPassword,
                passwordHash
        )) {
            throw new BusinessException(
                    ErrorCode.USER_CURRENT_PASSWORD_MISMATCH
            );
        }
    }

}
