package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 회원 탈퇴를 처리합니다.
 *
 * <p>현재 비밀번호를 확인한 뒤 사용자와 관심 개념을 논리 삭제하고,
 * 기존에 발급된 모든 인증 세션을 무효화합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class WithdrawUserService {

    private final UserRepository userRepository;
    private final UserInterestConceptRepository
            interestConceptRepository;
    private final PasswordHasher passwordHasher;
    private final AuthSessionLogoutAllStore
            authSessionLogoutAllStore;
    private final Clock clock;

    /**
     * 인증된 사용자를 탈퇴 처리합니다.
     *
     * <p>사용자 행에 비관적 쓰기 잠금을 적용하여 동일 사용자의
     * 프로필·관심 개념 변경 및 탈퇴 요청이 동시에 실행되는 경우를
     * 순차적으로 처리합니다.</p>
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

        User user =
                userRepository.findByIdForUpdate(
                        userId
                ).orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        validateActiveUser(user);

        validateCurrentPassword(
                command.currentPassword(),
                user.getPasswordHash()
        );

        softDeleteInterests(
                userId
        );

        user.softDelete(
                userId
        );

        /*
         * Redis를 갱신하기 전에 DB 오류를 확인할 수 있도록
         * saveAndFlush를 사용하는 Repository를 호출합니다.
         */
        userRepository.save(
                user
        );

        Instant invalidatedAt =
                clock.instant();

        authSessionLogoutAllStore.logoutAll(
                userId,
                invalidatedAt
        );
    }

    /**
     * 정상 이용 상태의 사용자인지 확인합니다.
     */
    private void validateActiveUser(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_ACTIVE
            );
        }
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

    /**
     * 사용자의 활성 관심 개념을 모두 논리 삭제합니다.
     */
    private void softDeleteInterests(
            UUID userId
    ) {
        List<UserInterestConcept> interests =
                interestConceptRepository.findAllByUserId(
                        userId
                );

        interests.forEach(
                interest -> interest.softDelete(
                        userId
                )
        );

        if (!interests.isEmpty()) {
            interestConceptRepository.saveAllAndFlush(
                    interests
            );
        }
    }
}
