package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 기본 정보 수정을 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class UpdateMyProfileService {

    private final UserRepository userRepository;
    private final EmailCipher emailCipher;

    /**
     * 인증된 사용자의 닉네임과 Java 학습 정보를 수정합니다.
     *
     * <p>다른 활성 사용자가 변경할 닉네임을 사용 중인지 사전에 확인하고,
     * 동시 요청으로 발생하는 닉네임 충돌은 DB Unique 제약과
     * UserRepository의 오류 변환으로 한 번 더 차단합니다.</p>
     *
     * @param userId 인증된 사용자 식별자
     * @param command 변경할 사용자 기본 정보
     * @return 변경된 사용자 기본 정보
     */
    @Transactional
    public GetMyProfileResult updateMyProfile(
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

        validateActiveUser(user);

        validateNicknameNotDuplicated(
                user,
                command.nickname()
        );

        user.updateProfile(
                command.nickname(),
                command.javaExperienceMonths(),
                command.learningLevel()
        );

        User savedUser =
                userRepository.save(user);

        String email = emailCipher.decrypt(
                savedUser.getEncryptedEmail()
        );

        return new GetMyProfileResult(
                savedUser.getId(),
                email,
                savedUser.getNickname(),
                savedUser.getRole(),
                savedUser.getStatus(),
                savedUser.getLearningLevel(),
                savedUser.getJavaExperienceMonths(),
                savedUser.getCreatedAt()
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
     * 변경할 닉네임을 다른 활성 사용자가 사용 중인지 확인합니다.
     *
     * <p>현재 사용자의 기존 닉네임과 대소문자 구분 없이 같으면
     * 자신의 닉네임이므로 중복 조회를 수행하지 않습니다.</p>
     */
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

        if (
                userRepository.existsByNicknameIgnoreCase(
                        nickname
                )
        ) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_NICKNAME
            );
        }
    }
}
