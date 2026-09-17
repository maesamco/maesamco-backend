package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateMyProfileService {

    private final UserRepository userRepository;
    private final EmailCipher emailCipher;

    @Transactional
    public UpdateMyProfileResult updateMyProfile(
            UUID userId,
            UpdateMyProfileCommand command
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                command,
                "사용자 정보 수정 명령은 필수입니다."
        );

        User user = userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        user.assertActive();

        validateNicknameNotDuplicated(
                user,
                command.nickname()
        );

        user.updateProfile(
                command.nickname(),
                command.javaExperienceMonths(),
                command.learningLevel()
        );

        User savedUser = save(user);

        String email = emailCipher.decrypt(
                savedUser.getEncryptedEmail()
        );

        return UpdateMyProfileResult.from(
                savedUser,
                email
        );
    }

    private User save(User user) {
        try {
            return userRepository.save(user);
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessException(
                    ErrorCode.USER_PROFILE_UPDATE_CONFLICT
            );
        }
    }

    private void validateNicknameNotDuplicated(
            User user,
            String nickname
    ) {
        boolean sameAsCurrentNickname =
                user.getNickname()
                        .equalsIgnoreCase(nickname);

        if (sameAsCurrentNickname) {
            return;
        }

        if (userRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_NICKNAME
            );
        }
    }
}
