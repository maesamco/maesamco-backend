package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.entity.XpSourceType;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XP 이력 keyset pagination을 실제 PostgreSQL에서 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        XpHistoryRepositoryImpl.class
})
@Testcontainers
class XpHistoryRepositoryKeysetIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private XpHistoryRepository xpHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName(
            "첫 페이지를 earnedAt DESC, id DESC 순서로 조회한다"
    )
    void findFirstPage_ordersByEarnedAtAndIdDescending() {
        UUID userId =
                persistUser(
                        'a',
                        "XpKeysetUserOne"
                );

        Instant olderTime =
                Instant.parse(
                        "2026-09-17T01:00:00Z"
                );

        Instant sameLatestTime =
                Instant.parse(
                        "2026-09-17T02:00:00Z"
                );

        XpHistory older =
                saveHistory(
                        userId,
                        olderTime,
                        10L
                );

        XpHistory sameTimeFirst =
                saveHistory(
                        userId,
                        sameLatestTime,
                        20L
                );

        XpHistory sameTimeSecond =
                saveHistory(
                        userId,
                        sameLatestTime,
                        30L
                );

        entityManager.clear();

        List<XpHistory> histories =
                xpHistoryRepository
                        .findFirstPageByUserId(
                                userId,
                                10
                        );

        List<UUID> expectedIds =
                new ArrayList<>(
                        List.of(
                                        sameTimeFirst.getId(),
                                        sameTimeSecond.getId()
                                )
                                .stream()
                                .sorted(
                                        Comparator
                                                .comparing(
                                                        UUID::toString
                                                )
                                                .reversed()
                                )
                                .toList()
                );

        expectedIds.add(
                older.getId()
        );

        assertThat(histories)
                .extracting(
                        XpHistory::getId
                )
                .containsExactlyElementsOf(
                        expectedIds
                );
    }

    @Test
    @DisplayName(
            "size 제한을 적용하고 cursor 이후 페이지를 중복과 누락 없이 조회한다"
    )
    void findPages_returnsAllHistoriesWithoutDuplicatesOrOmissions() {
        UUID userId =
                persistUser(
                        'b',
                        "XpKeysetUserTwo"
                );

        Instant firstTime =
                Instant.parse(
                        "2026-09-17T05:00:00Z"
                );

        Instant secondTime =
                Instant.parse(
                        "2026-09-17T04:00:00Z"
                );

        Instant thirdTime =
                Instant.parse(
                        "2026-09-17T03:00:00Z"
                );

        List<XpHistory> savedHistories =
                List.of(
                        saveHistory(
                                userId,
                                firstTime,
                                10L
                        ),
                        saveHistory(
                                userId,
                                firstTime,
                                20L
                        ),
                        saveHistory(
                                userId,
                                secondTime,
                                30L
                        ),
                        saveHistory(
                                userId,
                                secondTime,
                                40L
                        ),
                        saveHistory(
                                userId,
                                thirdTime,
                                50L
                        )
                );

        entityManager.clear();

        List<XpHistory> firstPage =
                xpHistoryRepository
                        .findFirstPageByUserId(
                                userId,
                                2
                        );

        XpHistory firstPageLast =
                firstPage.getLast();

        List<XpHistory> secondPage =
                xpHistoryRepository
                        .findNextPageByUserId(
                                userId,
                                firstPageLast.getEarnedAt(),
                                firstPageLast.getId(),
                                2
                        );

        XpHistory secondPageLast =
                secondPage.getLast();

        List<XpHistory> thirdPage =
                xpHistoryRepository
                        .findNextPageByUserId(
                                userId,
                                secondPageLast.getEarnedAt(),
                                secondPageLast.getId(),
                                2
                        );

        List<UUID> expectedIds =
                savedHistories.stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                XpHistory::getEarnedAt
                                        )
                                        .thenComparing(
                                                history ->
                                                        history.getId()
                                                                .toString()
                                        )
                                        .reversed()
                        )
                        .map(
                                XpHistory::getId
                        )
                        .toList();

        List<UUID> actualIds =
                new ArrayList<>();

        firstPage.stream()
                .map(XpHistory::getId)
                .forEach(actualIds::add);

        secondPage.stream()
                .map(XpHistory::getId)
                .forEach(actualIds::add);

        thirdPage.stream()
                .map(XpHistory::getId)
                .forEach(actualIds::add);

        assertThat(firstPage)
                .hasSize(2);

        assertThat(secondPage)
                .hasSize(2);

        assertThat(thirdPage)
                .hasSize(1);

        assertThat(actualIds)
                .containsExactlyElementsOf(
                        expectedIds
                )
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName(
            "다른 사용자의 XP 이력은 첫 페이지와 후속 페이지에서 제외한다"
    )
    void findPages_excludesOtherUsers() {
        UUID targetUserId =
                persistUser(
                        'c',
                        "XpKeysetTarget"
                );

        UUID otherUserId =
                persistUser(
                        'd',
                        "XpKeysetOther"
                );

        XpHistory targetLatest =
                saveHistory(
                        targetUserId,
                        Instant.parse(
                                "2026-09-17T03:00:00Z"
                        ),
                        20L
                );

        XpHistory targetOlder =
                saveHistory(
                        targetUserId,
                        Instant.parse(
                                "2026-09-17T01:00:00Z"
                        ),
                        10L
                );

        XpHistory otherHistory =
                saveHistory(
                        otherUserId,
                        Instant.parse(
                                "2026-09-17T02:00:00Z"
                        ),
                        10L
                );

        entityManager.clear();

        List<XpHistory> firstPage =
                xpHistoryRepository
                        .findFirstPageByUserId(
                                targetUserId,
                                1
                        );

        List<XpHistory> nextPage =
                xpHistoryRepository
                        .findNextPageByUserId(
                                targetUserId,
                                targetLatest.getEarnedAt(),
                                targetLatest.getId(),
                                10
                        );

        assertThat(firstPage)
                .extracting(XpHistory::getId)
                .containsExactly(
                        targetLatest.getId()
                )
                .doesNotContain(
                        otherHistory.getId()
                );

        assertThat(nextPage)
                .extracting(XpHistory::getId)
                .containsExactly(
                        targetOlder.getId()
                )
                .doesNotContain(
                        otherHistory.getId()
                );
    }

    @Test
    @DisplayName(
            "XP 이력이 없으면 빈 목록을 반환한다"
    )
    void findFirstPage_returnsEmptyList() {
        UUID userId =
                persistUser(
                        'e',
                        "XpKeysetEmpty"
                );

        List<XpHistory> histories =
                xpHistoryRepository
                        .findFirstPageByUserId(
                                userId,
                                21
                        );

        assertThat(histories)
                .isEmpty();
    }

    @Test
    @DisplayName(
            "XP 이력 조회 인덱스가 userId, earnedAt DESC, id DESC 순서로 생성된다"
    )
    void migration_createsCompositeQueryIndex() {
        String indexDefinition =
                (String) entityManager
                        .createNativeQuery(
                                """
                                SELECT indexdef
                                FROM pg_indexes
                                WHERE schemaname = 'user_schema'
                                  AND tablename = 'p_xp_histories'
                                  AND indexname =
                                      'idx_xp_histories_user_earned_at_id_desc'
                                """
                        )
                        .getSingleResult();

        assertThat(indexDefinition)
                .contains(
                        "user_id",
                        "earned_at DESC",
                        "id DESC"
                );
    }

    private XpHistory saveHistory(
            UUID userId,
            Instant earnedAt,
            long balanceAfter
    ) {
        return xpHistoryRepository.save(
                XpHistory.create(
                        userId,
                        UUID.randomUUID(),
                        RewardType.FIRST_CORRECT,
                        XpSourceType.SUBMISSION,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        10,
                        balanceAfter,
                        null,
                        "문제 최초 정답 보상",
                        earnedAt
                )
        );
    }

    private UUID persistUser(
            char marker,
            String nickname
    ) {
        User user =
                User.create(
                        "encrypted-email",
                        String.valueOf(marker)
                                .repeat(64),
                        "argon2-password-hash",
                        nickname,
                        3,
                        LearningLevel.BEGINNER
                );

        entityManager.persist(
                user
        );
        entityManager.flush();

        return user.getId();
    }
}
