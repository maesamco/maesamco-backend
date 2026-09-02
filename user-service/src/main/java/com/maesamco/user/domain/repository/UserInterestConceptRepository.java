package com.maesamco.user.domain.repository;

import com.maesamco.user.domain.entity.UserInterestConcept;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 관심 개념의 저장과 조회를 담당하는
 * 도메인 Repository 인터페이스입니다.
 *
 * <p>도메인 계층에서는 Spring Data JPA에 직접 의존하지 않고,
 * 이 인터페이스를 통해 영속성 계층에 접근합니다.</p>
 */
public interface UserInterestConceptRepository {

    /**
     * 사용자 관심 개념을 저장합니다.
     *
     * @param interestConcept 저장할 사용자 관심 개념
     * @return 저장된 사용자 관심 개념
     */
    UserInterestConcept save(
            UserInterestConcept interestConcept
    );

    /**
     * 관심 개념 설정 식별자로 사용자 관심 개념을 조회합니다.
     *
     * @param interestConceptId 관심 개념 설정 식별자
     * @return 조회 결과, 존재하지 않으면 빈 Optional
     */
    Optional<UserInterestConcept> findById(
            UUID interestConceptId
    );

    /**
     * 특정 사용자가 등록한 활성 관심 개념 목록을 조회합니다.
     *
     * <p>논리 삭제된 관심 개념은 조회 결과에서 제외됩니다.</p>
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
     * <p>관심 개념 등록 전에 호출하여 중복 등록을 방지합니다.</p>
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