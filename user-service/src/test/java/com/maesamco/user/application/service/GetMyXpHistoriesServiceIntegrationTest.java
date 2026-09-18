package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.entity.XpSourceType;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.infrastructure.persistence.UserRepositoryImpl;
import com.maesamco.user.infrastructure.persistence.XpHistoryRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 내 XP 이력 조회 서비스와 실제 PostgreSQL 연동을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        XpHistoryRepositoryImpl.class,
        XpHistoryCursorCodec.class,
        GetMyXpHistoriesService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GetMyXpHistoriesServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private GetMyXpHistoriesService getMyXpHistoriesService;

    @Autowired
    private XpHistoryCursorCodec cursorCodec;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private XpHistoryRepository xpHistoryRepository;

    @Test
    @DisplayName(
            "XP 이력을 earnedAt DESC와 id DESC 순서로 조회한다"
    )
    void getMyXpHistories_ordersByEarnedAtAndIdDescending() {
        User user =
                saveActiveUser(
                        101
                );

        XpHistory oldest =
                saveHistory(
                        user.getId(),
                        10,
                        10L,
                        "가장 오래된 이력",
                        Instant.parse(
                                "2026-09-17T01:00:00Z"
                        )
                );

        XpHistory tiedFirst =
                saveHistory(
                        user.getId(),
                        20,
                        30L,
                        "동일 시각 첫 번째 이력",
                        Instant.parse(
                                "2026-09-17T02:00:00Z"
                        )
                );

        XpHistory newest =
                saveHistory(
                        user.getId(),
                        40,
                        100L,
                        "가장 최신 이력",
                        Instant.parse(
                                "2026-09-17T03:00:00Z"
                        )
                );

        XpHistory tiedSecond =
                saveHistory(
                        user.getId(),
                        30,
                        60L,
                        "동일 시각 두 번째 이력",
                        Instant.parse(
                                "2026-09-17T02:00:00Z"
                        )
                );

        List<XpHistory> expectedOrder =
                Stream.of(
                                oldest,
                                tiedFirst,
                                newest,
                                tiedSecond
                        )
                        .sorted(
                                Comparator
                                        .comparing(
                                                XpHistory::getEarnedAt
                                        )
                                        .reversed()
                                        .thenComparing(
                                                history ->
                                                        history
                                                                .getId()
                                                                .toString(),
                                                Comparator.reverseOrder()
                                        )
                        )
                        .toList();

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                user.getId(),
                                new GetMyXpHistoriesQuery(
                                        10,
                                        null
                                )
                        );

        assertThat(result.items())
                .extracting(
                        GetMyXpHistoryItemResult::description
                )
                .containsExactlyElementsOf(
                        expectedOrder.stream()
                                .map(
                                        XpHistory::getDescription
                                )
                                .toList()
                );

        assertThat(result.items())
                .hasSize(4);

        GetMyXpHistoryItemResult firstItem =
                result.items().getFirst();

        assertThat(firstItem.rewardType())
                .isEqualTo(
                        RewardType.COACHING_COMPLETED
                );

        assertThat(firstItem.amount())
                .isEqualTo(40);

        assertThat(firstItem.balanceAfter())
                .isEqualTo(100L);

        assertThat(firstItem.description())
                .isEqualTo(
                        "가장 최신 이력"
                );

        assertThat(firstItem.earnedAt())
                .isEqualTo(
                        Instant.parse(
                                "2026-09-17T03:00:00Z"
                        )
                );

        assertThat(result.hasNext())
                .isFalse();

        assertThat(result.nextCursor())
                .isNull();
    }

    @Test
    @DisplayName(
            "cursor 기반 페이지 조회에서 중복과 누락 없이 모든 이력을 반환한다"
    )
    void getMyXpHistories_paginatesWithoutDuplicatesOrMissingItems() {
        User user =
                saveActiveUser(
                        102
                );

        saveHistory(
                user.getId(),
                10,
                10L,
                "이력 1",
                Instant.parse(
                        "2026-09-17T01:00:00Z"
                )
        );

        saveHistory(
                user.getId(),
                20,
                30L,
                "이력 2",
                Instant.parse(
                        "2026-09-17T02:00:00Z"
                )
        );

        saveHistory(
                user.getId(),
                30,
                60L,
                "이력 3",
                Instant.parse(
                        "2026-09-17T03:00:00Z"
                )
        );

        XpHistory fourth =
                saveHistory(
                        user.getId(),
                        40,
                        100L,
                        "이력 4",
                        Instant.parse(
                                "2026-09-17T04:00:00Z"
                        )
                );

        saveHistory(
                user.getId(),
                50,
                150L,
                "이력 5",
                Instant.parse(
                        "2026-09-17T05:00:00Z"
                )
        );

        GetMyXpHistoriesResult firstPage =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                user.getId(),
                                new GetMyXpHistoriesQuery(
                                        2,
                                        null
                                )
                        );

        assertThat(firstPage.items())
                .extracting(
                        GetMyXpHistoryItemResult::description
                )
                .containsExactly(
                        "이력 5",
                        "이력 4"
                );

        assertThat(firstPage.hasNext())
                .isTrue();

        assertThat(firstPage.nextCursor())
                .isNotBlank();

        XpHistoryCursor decodedFirstCursor =
                cursorCodec.decode(
                        firstPage.nextCursor()
                );

        assertThat(decodedFirstCursor.earnedAt())
                .isEqualTo(
                        fourth.getEarnedAt()
                );

        assertThat(decodedFirstCursor.xpHistoryId())
                .isEqualTo(
                        fourth.getId()
                );

        GetMyXpHistoriesResult secondPage =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                user.getId(),
                                new GetMyXpHistoriesQuery(
                                        2,
                                        firstPage.nextCursor()
                                )
                        );

        assertThat(secondPage.items())
                .extracting(
                        GetMyXpHistoryItemResult::description
                )
                .containsExactly(
                        "이력 3",
                        "이력 2"
                );

        assertThat(secondPage.hasNext())
                .isTrue();

        assertThat(secondPage.nextCursor())
                .isNotBlank();

        GetMyXpHistoriesResult lastPage =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                user.getId(),
                                new GetMyXpHistoriesQuery(
                                        2,
                                        secondPage.nextCursor()
                                )
                        );

        assertThat(lastPage.items())
                .extracting(
                        GetMyXpHistoryItemResult::description
                )
                .containsExactly(
                        "이력 1"
                );

        assertThat(lastPage.hasNext())
                .isFalse();

        assertThat(lastPage.nextCursor())
                .isNull();

        List<String> allDescriptions =
                Stream.of(
                                firstPage,
                                secondPage,
                                lastPage
                        )
                        .flatMap(
                                page ->
                                        page.items()
                                                .stream()
                        )
                        .map(
                                GetMyXpHistoryItemResult::description
                        )
                        .toList();

        assertThat(allDescriptions)
                .containsExactly(
                        "이력 5",
                        "이력 4",
                        "이력 3",
                        "이력 2",
                        "이력 1"
                )
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName(
            "다른 사용자의 XP 이력은 조회 결과에서 제외한다"
    )
    void getMyXpHistories_excludesOtherUsersHistories() {
        User requestedUser =
                saveActiveUser(
                        103
                );

        User otherUser =
                saveActiveUser(
                        104
                );

        saveHistory(
                requestedUser.getId(),
                10,
                10L,
                "조회 대상 이력 1",
                Instant.parse(
                        "2026-09-17T01:00:00Z"
                )
        );

        saveHistory(
                otherUser.getId(),
                999,
                999L,
                "다른 사용자 이력",
                Instant.parse(
                        "2026-09-17T03:00:00Z"
                )
        );

        saveHistory(
                requestedUser.getId(),
                20,
                30L,
                "조회 대상 이력 2",
                Instant.parse(
                        "2026-09-17T02:00:00Z"
                )
        );

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                requestedUser.getId(),
                                new GetMyXpHistoriesQuery(
                                        10,
                                        null
                                )
                        );

        assertThat(result.items())
                .extracting(
                        GetMyXpHistoryItemResult::description
                )
                .containsExactly(
                        "조회 대상 이력 2",
                        "조회 대상 이력 1"
                )
                .doesNotContain(
                        "다른 사용자 이력"
                );
    }

    @Test
    @DisplayName(
            "XP 이력이 없는 활성 사용자는 빈 페이지를 반환한다"
    )
    void getMyXpHistories_returnsEmptyPage() {
        User user =
                saveActiveUser(
                        105
                );

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                user.getId(),
                                GetMyXpHistoriesQuery.of(
                                        null,
                                        null
                                )
                        );

        assertThat(result.items())
                .isEmpty();

        assertThat(result.hasNext())
                .isFalse();

        assertThat(result.nextCursor())
                .isNull();
    }

    @Test
    @DisplayName(
            "잘못된 cursor는 사용자 조회 전에 INVALID_INPUT_VALUE로 거부한다"
    )
    void getMyXpHistories_rejectsInvalidCursor() {
        UUID unknownUserId =
                UUID.fromString(
                        "99999999-9999-9999-9999-999999999998"
                );

        assertThatThrownBy(
                () ->
                        getMyXpHistoriesService
                                .getMyXpHistories(
                                        unknownUserId,
                                        new GetMyXpHistoriesQuery(
                                                20,
                                                "invalid-cursor"
                                        )
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName(
            "정지 사용자는 XP 이력이 존재해도 USER_NOT_ACTIVE로 거부한다"
    )
    void getMyXpHistories_rejectsSuspendedUser() {
        User user =
                saveActiveUser(
                        106
                );

        saveHistory(
                user.getId(),
                10,
                10L,
                "정지 사용자 이력",
                Instant.parse(
                        "2026-09-17T01:00:00Z"
                )
        );

        user.suspend();

        userRepository.save(
                user
        );

        assertThatThrownBy(
                () ->
                        getMyXpHistoriesService
                                .getMyXpHistories(
                                        user.getId(),
                                        new GetMyXpHistoriesQuery(
                                                20,
                                                null
                                        )
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_NOT_ACTIVE
                );
    }

    @Test
    @DisplayName(
            "논리 삭제된 사용자는 XP 이력이 존재해도 USER_NOT_FOUND로 처리한다"
    )
    void getMyXpHistories_rejectsWithdrawnUser() {
        User user =
                saveActiveUser(
                        107
                );

        saveHistory(
                user.getId(),
                10,
                10L,
                "탈퇴 사용자 이력",
                Instant.parse(
                        "2026-09-17T01:00:00Z"
                )
        );

        user.softDelete(
                user.getId(),
                Instant.parse(
                        "2026-09-17T02:00:00Z"
                )
        );

        userRepository.save(
                user
        );

        assertThatThrownBy(
                () ->
                        getMyXpHistoriesService
                                .getMyXpHistories(
                                        user.getId(),
                                        new GetMyXpHistoriesQuery(
                                                20,
                                                null
                                        )
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_NOT_FOUND
                );
    }

    @Test
    @DisplayName(
            "존재하지 않는 사용자는 USER_NOT_FOUND를 반환한다"
    )
    void getMyXpHistories_rejectsUnknownUser() {
        UUID unknownUserId =
                UUID.fromString(
                        "99999999-9999-9999-9999-999999999999"
                );

        assertThatThrownBy(
                () ->
                        getMyXpHistoriesService
                                .getMyXpHistories(
                                        unknownUserId,
                                        new GetMyXpHistoriesQuery(
                                                20,
                                                null
                                        )
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_NOT_FOUND
                );
    }

    private User saveActiveUser(
            int sequence
    ) {
        return userRepository.save(
                User.create(
                        "encrypted-email-" + sequence,
                        String.format(
                                "%064x",
                                sequence
                        ),
                        "password-hash-" + sequence,
                        "XP테스트사용자" + sequence,
                        sequence,
                        LearningLevel.BEGINNER
                )
        );
    }

    private XpHistory saveHistory(
            UUID userId,
            int amount,
            long balanceAfter,
            String description,
            Instant earnedAt
    ) {
        return xpHistoryRepository.save(
                XpHistory.create(
                        userId,
                        UUID.randomUUID(),
                        RewardType.COACHING_COMPLETED,
                        XpSourceType.COACHING,
                        UUID.randomUUID(),
                        null,
                        amount,
                        balanceAfter,
                        null,
                        description,
                        earnedAt
                )
        );
    }
}
