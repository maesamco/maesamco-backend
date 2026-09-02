package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.UserGamificationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA를 이용해 사용자 게이미피케이션 상태에 접근하는 내부 Repository입니다.
 *
 * <p>이 인터페이스는 인프라 계층 내부에서만 사용하며,
 * 응용 계층에서는 도메인의 Repository 포트를 사용합니다.</p>
 */
public interface SpringDataUserGamificationStateRepository
        extends JpaRepository<UserGamificationState, UUID> {
}
