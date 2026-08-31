package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 도메인의 UserInterestConceptRepository를
 * Spring Data JPA로 구현하는 영속성 어댑터입니다.
 *
 * <p>도메인 계층의 저장·조회 요청을
 * SpringDataUserInterestConceptRepository에 위임하여
 * 도메인 계층과 JPA 구현 기술 사이의 의존성을 분리합니다.</p>
 */
@Repository
@RequiredArgsConstructor
public class UserInterestConceptRepositoryImpl
        implements UserInterestConceptRepository {

    /**
     * 실제 JPA 저장과 조회를 담당하는 내부 Repository입니다.
     */
    private final SpringDataUserInterestConceptRepository
            springDataRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public UserInterestConcept save(
            UserInterestConcept interestConcept
    ) {
        return springDataRepository.save(interestConcept);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<UserInterestConcept> findById(
            UUID interestConceptId
    ) {
        return springDataRepository.findById(interestConceptId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UserInterestConcept> findAllByUserId(
            UUID userId
    ) {
        return springDataRepository.findAllByUserId(userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByUserIdAndConceptId(
            UUID userId,
            UUID conceptId
    ) {
        return springDataRepository
                .existsByUserIdAndConceptId(
                        userId,
                        conceptId
                );
    }
}