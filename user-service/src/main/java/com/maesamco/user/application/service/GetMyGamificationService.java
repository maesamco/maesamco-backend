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

import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 현재 게이미피케이션 상태 조회를 처리합니다.
 *
 * <p>계정 상태를 확인하기 위한 프로필 조회와 달리,
 * XP, 레벨과 스트릭은 서비스 이용 중 쌓이는 진행 정보이므로
 * 정상적으로 서비스를 이용할 수 있는 활성 사용자에게만 제공합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class GetMyGamificationService {

    private final UserRepository userRepository;

    private final UserGamificationStateRepository
            userGamificationStateRepository;

    /**
     * 인증된 활성 사용자의 XP, 레벨과 스트릭 상태를 조회합니다.
     *
     * @param userId 인증된 사용자 식별자
     * @return 사용자의 현재 게이미피케이션 상태
     */
    @Transactional(readOnly = true)
    public GetMyGamificationResult getMyGamification(
            UUID userId
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.USER_NOT_FOUND
                                )
                        );

        user.assertActive();

        UserGamificationState state =
                userGamificationStateRepository
                        .findByUserId(userId)
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.GAMIFICATION_STATE_NOT_FOUND
                                )
                        );

        return GetMyGamificationResult.from(
                state
        );
    }
}
