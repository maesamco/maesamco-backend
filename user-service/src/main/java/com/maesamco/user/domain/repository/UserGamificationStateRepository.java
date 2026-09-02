package com.maesamco.user.domain.repository;

import com.maesamco.user.domain.entity.UserGamificationState;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 게이미피케이션 상태의 영속성 기능을 정의하는 Repository 포트입니다.
 *
 * <p>도메인 계층이 Spring Data JPA에 직접 의존하지 않도록
 * 저장 및 사용자별 조회 기능만 추상화합니다.</p>
 */
public interface UserGamificationStateRepository {

    /**
     * 사용자 게이미피케이션 상태를 저장하거나 변경 내용을 반영합니다.
     *
     * @param state 저장할 게이미피케이션 상태
     * @return 저장된 게이미피케이션 상태
     */
    UserGamificationState save(UserGamificationState state);

    /**
     * 사용자 식별자로 게이미피케이션 상태를 조회합니다.
     *
     * @param userId 사용자 식별자
     * @return 조회된 상태, 존재하지 않으면 빈 Optional
     */
    Optional<UserGamificationState> findByUserId(UUID userId);
}
