package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.User;
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
 * 로그인 사용자의 비밀번호 변경을 처리합니다.
 *
 * <p>현재 비밀번호 확인, 새 비밀번호 정책 검증,
 * 비밀번호 해시 변경 및 기존 인증 세션 무효화를 조율합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final EmailCipher emailCipher;
    private final AuthSessionLogoutAllStore authSessionLogoutAllStore;
    private final Clock clock;

    /**
     * 인증된 사용자의 비밀번호를 변경하고
     * 기존에 발급된 모든 인증 세션을 무효화합니다.
     *
     * @param userId 인증된 사용자 식별자
     * @param command 비밀번호 변경 입력값
     */
    @Transactional
    public void changePassword(
            UUID userId,
            ChangePasswordCommand command
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                command,
                "비밀번호 변경 명령은 필수입니다."
        );

        User user = userRepository.findById(userId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        user.assertActive();

        /*
         * 소셜 계정으로만 가입한 사용자는 변경할 비밀번호가 없습니다(#308).
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

        validateNewPassword(
                command.newPassword(),
                user
        );

        String newPasswordHash =
                passwordHasher.hash(
                        command.newPassword()
                );

        user.changePasswordHash(
                newPasswordHash
        );

        userRepository.save(user);

        Instant invalidatedAt =
                clock.instant();

        authSessionLogoutAllStore.logoutAll(
                userId,
                invalidatedAt
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

    /**
     * 새 비밀번호가 현재 비밀번호, 이메일 또는 닉네임과
     * 동일하지 않은지 확인합니다.
     */
    private void validateNewPassword(
            String newPassword,
            User user
    ) {
        if (passwordHasher.matches(
                newPassword,
                user.getPasswordHash()
        )) {
            throw new BusinessException(
                    ErrorCode.USER_PASSWORD_POLICY_VIOLATION
            );
        }

        String email =
                emailCipher.decrypt(
                        user.getEncryptedEmail()
                );

        boolean sameAsEmail =
                newPassword != null
                        && newPassword.equalsIgnoreCase(email);

        boolean sameAsNickname =
                newPassword != null
                        && newPassword.equalsIgnoreCase(
                        user.getNickname()
                );

        if (sameAsEmail || sameAsNickname) {
            throw new BusinessException(
                    ErrorCode.USER_PASSWORD_POLICY_VIOLATION
            );
        }
    }
}
