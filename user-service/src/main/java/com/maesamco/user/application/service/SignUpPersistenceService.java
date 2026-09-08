package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 과정의 DB 저장 작업을 담당합니다.
 *
 * <p>User와 초기 게이미피케이션 상태 저장을 하나의 DB 트랜잭션으로 묶습니다.
 * 비밀번호 해싱, JWT 발급, Redis 인증 세션 저장과 같은 작업은
 * 이 트랜잭션에 포함하지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class SignUpPersistenceService {

    private final UserRepository userRepository;
    private final UserGamificationStateRepository
            gamificationStateRepository;

    /**
     * 사용자와 초기 게이미피케이션 상태를
     * 하나의 DB 트랜잭션으로 저장합니다.
     *
     * @param user 저장할 사용자
     * @return 저장된 사용자
     */
    @Transactional
    public User saveUser(User user) {
        validateNotDuplicated(
                user.getEmailLookupHash(),
                user.getNickname()
        );

        User savedUser =
                userRepository.save(user);

        UserGamificationState gamificationState =
                UserGamificationState.create(
                        savedUser.getId()
                );

        gamificationStateRepository.save(
                gamificationState
        );

        return savedUser;
    }

    /**
     * 이메일 또는 닉네임이
     * 미삭제 사용자와 중복되는지 확인합니다.
     */
    private void validateNotDuplicated(
            String emailLookupHash,
            String nickname
    ) {
        if (userRepository.existsByEmailLookupHash(
                emailLookupHash
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_EMAIL
            );
        }

        if (nickname != null
                && !nickname.isBlank()
                && userRepository.existsByNicknameIgnoreCase(
                nickname
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_NICKNAME
            );
        }
    }
}
