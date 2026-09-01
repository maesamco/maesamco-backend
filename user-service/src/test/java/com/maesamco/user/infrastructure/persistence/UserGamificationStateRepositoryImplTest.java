package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserGamificationStateRepository 구현체의 PostgreSQL 통합 테스트입니다.
 *
 * <p>Flyway 베이스라인은 운영 스키마 검증에 사용하고, 이 테스트는 격리된
 * 컨테이너에서 {@code ddl-auto=create-drop}으로 테이블을 생성합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@EnableJpaAuditing
@Testcontainers
class UserGamificationStateRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    @Autowired
    private SpringDataUserGamificationStateRepository
            springDataRepository;

    @Autowired
    private EntityManager entityManager;

    private UserGamificationStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new UserGamificationStateRepositoryImpl(
                springDataRepository
        );
    }

    @Test
    @DisplayName("게이미피케이션 상태를 저장한 뒤 사용자 ID로 조회할 수 있다")
    void saveAndFindByUserId_returnsState() {
        // given
        UUID userId = UUID.randomUUID();
        UserGamificationState state =
                UserGamificationState.create(userId);

        // when
        repository.save(state);
        springDataRepository.flush();
        entityManager.clear();

        UserGamificationState foundState =
                repository.findByUserId(userId).orElseThrow();

        // then
        assertThat(foundState.getUserId()).isEqualTo(userId);
        assertThat(foundState.getTotalXp()).isZero();
        assertThat(foundState.getLevel()).isEqualTo(1);
        assertThat(foundState.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 게이미피케이션 상태를 조회하면 빈 결과를 반환한다")
    void findByUserId_whenMissing_returnsEmpty() {
        // when
        Optional<UserGamificationState> foundState =
                repository.findByUserId(UUID.randomUUID());

        // then
        assertThat(foundState).isEmpty();
    }

    @Test
    @DisplayName("XP와 스트릭 변경 내용을 PostgreSQL에 반영한다")
    void saveChangedState_persistsChanges() {
        // given
        UUID userId = UUID.randomUUID();
        UserGamificationState state = repository.save(
                UserGamificationState.create(userId)
        );

        springDataRepository.flush();

        // when
        state.applyXp(120L, 2);
        state.recordActivity(LocalDate.of(2026, 8, 31));
        repository.save(state);

        springDataRepository.flush();
        entityManager.clear();

        UserGamificationState foundState =
                repository.findByUserId(userId).orElseThrow();

        // then
        assertThat(foundState.getTotalXp()).isEqualTo(120L);
        assertThat(foundState.getLevel()).isEqualTo(2);
        assertThat(foundState.getCurrentStreak()).isEqualTo(1);
        assertThat(foundState.getLongestStreak()).isEqualTo(1);
        assertThat(foundState.getLastActivityDate())
                .isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(foundState.getVersion()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("오래된 버전으로 저장하면 게이미피케이션 상태 충돌 예외를 반환한다")
    void saveWithStaleVersion_throwsConflict() {
        // given
        UUID userId = UUID.randomUUID();
        repository.save(UserGamificationState.create(userId));
        entityManager.clear();

        UserGamificationState staleState =
                repository.findByUserId(userId).orElseThrow();
        entityManager.detach(staleState);

        UserGamificationState currentState =
                repository.findByUserId(userId).orElseThrow();
        currentState.applyXp(100L, 2);
        repository.save(currentState);
        entityManager.clear();

        staleState.applyXp(50L, 1);

        // when & then
        assertThatThrownBy(() -> repository.save(staleState))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.GAMIFICATION_STATE_CONFLICT
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "게이미피케이션 상태가 동시에 변경되었습니다. 다시 시도해주세요."
                                    );
                        }
                );
    }
}
