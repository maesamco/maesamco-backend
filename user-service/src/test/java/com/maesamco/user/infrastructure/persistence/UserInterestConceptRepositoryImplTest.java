package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserInterestConceptRepository 구현체의
 * PostgreSQL 통합 테스트입니다.
 *
 * <p>실제 PostgreSQL Testcontainers를 사용하여
 * 엔티티 매핑과 저장·조회·중복 확인 및
 * 논리 삭제 조건을 검증합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(JpaAuditingConfig.class)
@Testcontainers
class UserInterestConceptRepositoryImplTest {

    /**
     * 테스트에서 사용할 임시 PostgreSQL 컨테이너입니다.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    /**
     * 실제 Spring Data JPA Repository입니다.
     */
    @Autowired
    private SpringDataUserInterestConceptRepository
            springDataRepository;

    /**
     * 영속성 컨텍스트 초기화에 사용하는 EntityManager입니다.
     */
    @Autowired
    private EntityManager entityManager;

    /**
     * 테스트 대상 도메인 Repository입니다.
     */
    private UserInterestConceptRepository interestConceptRepository;

    /**
     * 도메인 Repository가 실제 JPA Repository를
     * 사용하도록 구성합니다.
     */
    @BeforeEach
    void setUp() {
        interestConceptRepository =
                new UserInterestConceptRepositoryImpl(
                        springDataRepository
                );
    }

    /**
     * 관심 개념을 저장한 뒤 식별자로 조회할 수 있는지 검증합니다.
     */
    @Test
    @DisplayName("관심 개념을 저장한 뒤 ID로 조회할 수 있다")
    void saveAndFindById() {
        // given
        UserInterestConcept interestConcept =
                createInterestConcept();

        // when
        UserInterestConcept savedInterestConcept =
                interestConceptRepository.save(interestConcept);

        springDataRepository.flush();
        entityManager.clear();

        Optional<UserInterestConcept> foundInterestConcept =
                interestConceptRepository.findById(
                        savedInterestConcept.getId()
                );

        // then
        assertThat(foundInterestConcept).isPresent();
        assertThat(foundInterestConcept.get().getUserId())
                .isEqualTo(savedInterestConcept.getUserId());
        assertThat(foundInterestConcept.get().getConceptId())
                .isEqualTo(savedInterestConcept.getConceptId());
        assertThat(foundInterestConcept.get().getCreatedAt())
                .isNotNull();
    }

    /**
     * 특정 사용자의 관심 개념만 조회되는지 검증합니다.
     */
    @Test
    @DisplayName("사용자별로 활성 관심 개념 목록을 조회한다")
    void findAllByUserId() {
        // given
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        UUID firstConceptId = UUID.randomUUID();
        UUID secondConceptId = UUID.randomUUID();

        interestConceptRepository.save(
                UserInterestConcept.create(
                        firstUserId,
                        firstConceptId
                )
        );

        interestConceptRepository.save(
                UserInterestConcept.create(
                        firstUserId,
                        secondConceptId
                )
        );

        interestConceptRepository.save(
                UserInterestConcept.create(
                        secondUserId,
                        UUID.randomUUID()
                )
        );

        springDataRepository.flush();
        entityManager.clear();

        // when
        List<UserInterestConcept> foundInterestConcepts =
                interestConceptRepository.findAllByUserId(
                        firstUserId
                );

        // then
        assertThat(foundInterestConcepts).hasSize(2);
        assertThat(foundInterestConcepts)
                .extracting(UserInterestConcept::getConceptId)
                .containsExactlyInAnyOrder(
                        firstConceptId,
                        secondConceptId
                );
    }

    /**
     * 동일한 사용자와 개념의 등록 여부를 확인할 수 있는지 검증합니다.
     */
    @Test
    @DisplayName("사용자와 개념 조합의 중복 등록 여부를 확인한다")
    void existsByUserIdAndConceptId() {
        // given
        UUID userId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();

        interestConceptRepository.save(
                UserInterestConcept.create(
                        userId,
                        conceptId
                )
        );

        springDataRepository.flush();
        entityManager.clear();

        // when
        boolean existingInterest =
                interestConceptRepository
                        .existsByUserIdAndConceptId(
                                userId,
                                conceptId
                        );

        boolean missingInterest =
                interestConceptRepository
                        .existsByUserIdAndConceptId(
                                userId,
                                UUID.randomUUID()
                        );

        // then
        assertThat(existingInterest).isTrue();
        assertThat(missingInterest).isFalse();
    }

    /**
     * 논리 삭제된 관심 개념이 일반 조회에서
     * 제외되는지 검증합니다.
     */
    @Test
    @DisplayName("논리 삭제된 관심 개념은 조회와 중복 확인에서 제외된다")
    void excludeSoftDeletedInterestConcept() {
        // given
        UUID userId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();

        UserInterestConcept savedInterestConcept =
                interestConceptRepository.save(
                        UserInterestConcept.create(
                                userId,
                                conceptId
                        )
                );

        springDataRepository.flush();

        UUID interestConceptId =
                savedInterestConcept.getId();

        // when
        savedInterestConcept.softDelete(
                UUID.randomUUID()
        );

        springDataRepository.flush();
        entityManager.clear();

        // then
        assertThat(
                interestConceptRepository.findById(
                        interestConceptId
                )
        ).isEmpty();

        assertThat(
                interestConceptRepository.findAllByUserId(
                        userId
                )
        ).isEmpty();

        assertThat(
                interestConceptRepository
                        .existsByUserIdAndConceptId(
                                userId,
                                conceptId
                        )
        ).isFalse();
    }

    /**
     * 논리 삭제 후 동일한 관심 개념을
     * 다시 등록할 수 있는지 검증합니다.
     */
    @Test
    @DisplayName("논리 삭제 후 동일한 관심 개념을 다시 등록할 수 있다")
    void recreateAfterSoftDelete() {
        // given
        UUID userId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();

        UserInterestConcept deletedInterestConcept =
                interestConceptRepository.save(
                        UserInterestConcept.create(
                                userId,
                                conceptId
                        )
                );

        springDataRepository.flush();

        deletedInterestConcept.softDelete(
                UUID.randomUUID()
        );

        springDataRepository.flush();
        entityManager.clear();

        // when
        UserInterestConcept recreatedInterestConcept =
                interestConceptRepository.save(
                        UserInterestConcept.create(
                                userId,
                                conceptId
                        )
                );

        springDataRepository.flush();
        entityManager.clear();

        // then
        List<UserInterestConcept> foundInterestConcepts =
                interestConceptRepository.findAllByUserId(
                        userId
                );

        assertThat(foundInterestConcepts).hasSize(1);
        assertThat(foundInterestConcepts.getFirst().getId())
                .isEqualTo(recreatedInterestConcept.getId());
    }

    /**
     * 테스트에서 사용할 사용자 관심 개념을 생성합니다.
     */
    private UserInterestConcept createInterestConcept() {
        return UserInterestConcept.create(
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
