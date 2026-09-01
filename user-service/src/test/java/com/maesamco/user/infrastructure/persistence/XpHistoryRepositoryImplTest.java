package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.entity.XpSourceType;
import com.maesamco.user.domain.repository.XpHistoryRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XpHistoryRepository 구현체의 PostgreSQL 통합 테스트입니다.
 *
 * <p>H2가 아닌 실제 PostgreSQL Testcontainers를 사용하여
 * XP 이력 저장, 최신순 조회 및 중복 확인 쿼리를 검증합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@EnableJpaAuditing
@Testcontainers
class XpHistoryRepositoryImplTest {

    /**
     * 테스트에서 사용할 임시 PostgreSQL 컨테이너입니다.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    @Autowired
    private SpringDataXpHistoryRepository springDataXpHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    private XpHistoryRepository xpHistoryRepository;

    /**
     * 도메인 Repository가 실제 JPA Repository를 사용하도록 구성합니다.
     */
    @BeforeEach
    void setUp() {
        xpHistoryRepository =
                new XpHistoryRepositoryImpl(
                        springDataXpHistoryRepository
                );
    }

    @Test
    @DisplayName("XP 이력을 저장할 수 있다")
    void save_persistsXpHistory() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        XpHistory xpHistory = createFirstCorrectHistory(
                userId,
                UUID.randomUUID(),
                problemId,
                10,
                10L,
                Instant.parse("2026-09-01T01:00:00Z")
        );

        // when
        XpHistory savedHistory =
                xpHistoryRepository.save(xpHistory);

        springDataXpHistoryRepository.flush();
        entityManager.clear();

        XpHistory foundHistory =
                springDataXpHistoryRepository
                        .findById(savedHistory.getId())
                        .orElseThrow();

        // then
        assertThat(foundHistory.getId())
                .isEqualTo(savedHistory.getId());
        assertThat(foundHistory.getUserId())
                .isEqualTo(userId);
        assertThat(foundHistory.getCreatedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("사용자의 XP 이력을 획득 시각 기준 최신순으로 조회한다")
    void findAllByUserIdOrderByEarnedAtDesc_returnsLatestFirst() {
        // given
        UUID userId = UUID.randomUUID();

        XpHistory olderHistory = createFirstCorrectHistory(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                10,
                10L,
                Instant.parse("2026-09-01T01:00:00Z")
        );

        XpHistory newerHistory = createFirstCorrectHistory(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                10,
                20L,
                Instant.parse("2026-09-01T02:00:00Z")
        );

        xpHistoryRepository.save(olderHistory);
        xpHistoryRepository.save(newerHistory);

        springDataXpHistoryRepository.flush();
        entityManager.clear();

        // when
        List<XpHistory> histories =
                xpHistoryRepository
                        .findAllByUserIdOrderByEarnedAtDesc(userId);

        // then
        assertThat(histories)
                .extracting(XpHistory::getId)
                .containsExactly(
                        newerHistory.getId(),
                        olderHistory.getId()
                );
    }

    @Test
    @DisplayName("동일한 원천 이벤트 ID의 XP 이력 존재 여부를 확인한다")
    void existsBySourceEventId_returnsCorrectResult() {
        // given
        UUID sourceEventId = UUID.randomUUID();

        xpHistoryRepository.save(
                createFirstCorrectHistory(
                        UUID.randomUUID(),
                        sourceEventId,
                        UUID.randomUUID(),
                        10,
                        10L,
                        Instant.parse("2026-09-01T01:00:00Z")
                )
        );

        springDataXpHistoryRepository.flush();
        entityManager.clear();

        // when
        boolean existing =
                xpHistoryRepository
                        .existsBySourceEventId(sourceEventId);

        boolean missing =
                xpHistoryRepository
                        .existsBySourceEventId(UUID.randomUUID());

        // then
        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    @Test
    @DisplayName("동일 사용자와 문제의 최초 정답 보상 존재 여부를 확인한다")
    void existsFirstCorrectReward_returnsCorrectResult() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        xpHistoryRepository.save(
                createFirstCorrectHistory(
                        userId,
                        UUID.randomUUID(),
                        problemId,
                        10,
                        10L,
                        Instant.parse("2026-09-01T01:00:00Z")
                )
        );

        springDataXpHistoryRepository.flush();
        entityManager.clear();

        // when
        boolean existing =
                xpHistoryRepository
                        .existsByUserIdAndProblemIdAndRewardType(
                                userId,
                                problemId,
                                RewardType.FIRST_CORRECT
                        );

        boolean missing =
                xpHistoryRepository
                        .existsByUserIdAndProblemIdAndRewardType(
                                userId,
                                UUID.randomUUID(),
                                RewardType.FIRST_CORRECT
                        );

        // then
        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    @Test
    @DisplayName("동일 사용자와 날짜의 일일 목표 보상 존재 여부를 확인한다")
    void existsDailyGoalReward_returnsCorrectResult() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDate rewardDate = LocalDate.of(2026, 9, 1);

        xpHistoryRepository.save(
                createDailyGoalHistory(
                        userId,
                        UUID.randomUUID(),
                        rewardDate,
                        20,
                        20L,
                        Instant.parse("2026-09-01T03:00:00Z")
                )
        );

        springDataXpHistoryRepository.flush();
        entityManager.clear();

        // when
        boolean existing =
                xpHistoryRepository
                        .existsByUserIdAndRewardDateAndRewardType(
                                userId,
                                rewardDate,
                                RewardType.DAILY_GOAL_COMPLETED
                        );

        boolean missing =
                xpHistoryRepository
                        .existsByUserIdAndRewardDateAndRewardType(
                                userId,
                                rewardDate.plusDays(1),
                                RewardType.DAILY_GOAL_COMPLETED
                        );

        // then
        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    /**
     * 최초 정답 XP 이력을 생성합니다.
     */
    private XpHistory createFirstCorrectHistory(
            UUID userId,
            UUID sourceEventId,
            UUID problemId,
            int amount,
            long balanceAfter,
            Instant earnedAt
    ) {
        return XpHistory.create(
                userId,
                sourceEventId,
                RewardType.FIRST_CORRECT,
                XpSourceType.SUBMISSION,
                UUID.randomUUID(),
                problemId,
                amount,
                balanceAfter,
                null,
                "최초 정답 보상",
                earnedAt
        );
    }

    /**
     * 일일 목표 완료 XP 이력을 생성합니다.
     */
    private XpHistory createDailyGoalHistory(
            UUID userId,
            UUID sourceEventId,
            LocalDate rewardDate,
            int amount,
            long balanceAfter,
            Instant earnedAt
    ) {
        return XpHistory.create(
                userId,
                sourceEventId,
                RewardType.DAILY_GOAL_COMPLETED,
                XpSourceType.DAILY_ACTIVITY,
                UUID.randomUUID(),
                null,
                amount,
                balanceAfter,
                rewardDate,
                "일일 목표 완료 보상",
                earnedAt
        );
    }
}
