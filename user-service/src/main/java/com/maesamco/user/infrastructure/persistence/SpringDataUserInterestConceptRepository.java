package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.UserInterestConcept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA를 이용해 UserInterestConcept 엔티티에 접근하는
 * 인프라 계층 내부 Repository입니다.
 *
 * <p>애플리케이션 계층에서는 이 인터페이스를 직접 사용하지 않고,
 * 도메인의 UserInterestConceptRepository를 사용합니다.</p>
 */
public interface SpringDataUserInterestConceptRepository
        extends JpaRepository<UserInterestConcept, UUID> {

    /**
     * 특정 사용자가 등록한 활성 관심 개념 목록을 조회합니다.
     *
     * @param userId 사용자 식별자
     * @return 사용자의 활성 관심 개념 목록
     */
    List<UserInterestConcept> findAllByUserId(
            UUID userId
    );

    /**
     * 동일한 사용자와 개념으로 등록된 활성 관심 개념이
     * 존재하는지 확인합니다.
     *
     * @param userId 사용자 식별자
     * @param conceptId Content Service의 개념 식별자
     * @return 이미 등록되어 있으면 true
     */
    boolean existsByUserIdAndConceptId(
            UUID userId,
            UUID conceptId
    );
}