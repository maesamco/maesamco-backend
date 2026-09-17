package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetMyXpHistoriesServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private UserRepository userRepository;

    private XpHistoryRepository xpHistoryRepository;

    private XpHistoryCursorCodec cursorCodec;

    private GetMyXpHistoriesService getMyXpHistoriesService;

    @BeforeEach
    void setUp() {
        userRepository =
                mock(
                        UserRepository.class
                );

        xpHistoryRepository =
                mock(
                        XpHistoryRepository.class
                );

        cursorCodec =
                new XpHistoryCursorCodec();

        getMyXpHistoriesService =
                new GetMyXpHistoriesService(
                        userRepository,
                        xpHistoryRepository,
                        cursorCodec
                );
    }

    @Test
    @DisplayName(
            "첫 페이지를 size보다 한 건 더 조회하고 다음 cursor를 생성한다"
    )
    void getMyXpHistories_returnsFirstPageWithNextCursor() {
        User user =
                arrangeActiveUser();

        XpHistory first =
                mockHistory(
                        UUID.fromString(
                                "ffffffff-ffff-ffff-ffff-ffffffffffff"
                        ),
                        Instant.parse(
                                "2026-09-17T03:00:00Z"
                        ),
                        30L
                );

        XpHistory second =
                mockHistory(
                        UUID.fromString(
                                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
                        ),
                        Instant.parse(
                                "2026-09-17T02:00:00Z"
                        ),
                        20L
                );

        XpHistory extra =
                mockHistory(
                        UUID.fromString(
                                "dddddddd-dddd-dddd-dddd-dddddddddddd"
                        ),
                        Instant.parse(
                                "2026-09-17T01:00:00Z"
                        ),
                        10L
                );

        when(
                xpHistoryRepository
                        .findFirstPageByUserId(
                                USER_ID,
                                3
                        )
        )
                .thenReturn(
                        List.of(
                                first,
                                second,
                                extra
                        )
                );

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                new GetMyXpHistoriesQuery(
                                        2,
                                        null
                                )
                        );

        assertThat(result.items())
                .hasSize(2);

        assertThat(result.items())
                .extracting(
                        GetMyXpHistoryItemResult::balanceAfter
                )
                .containsExactly(
                        30L,
                        20L
                );

        assertThat(result.hasNext())
                .isTrue();

        assertThat(result.nextCursor())
                .isNotBlank();

        XpHistoryCursor decodedCursor =
                cursorCodec.decode(
                        result.nextCursor()
                );

        assertThat(decodedCursor)
                .isEqualTo(
                        new XpHistoryCursor(
                                second.getEarnedAt(),
                                second.getId()
                        )
                );

        assertThatThrownBy(
                () -> result.items()
                        .clear()
        )
                .isInstanceOf(
                        UnsupportedOperationException.class
                );

        verify(user)
                .assertActive();

        verify(xpHistoryRepository)
                .findFirstPageByUserId(
                        USER_ID,
                        3
                );

        verify(
                xpHistoryRepository,
                never()
        )
                .findNextPageByUserId(
                        USER_ID,
                        second.getEarnedAt(),
                        second.getId(),
                        3
                );
    }

    @Test
    @DisplayName(
            "cursor가 있으면 cursor 이후 이력을 size보다 한 건 더 조회한다"
    )
    void getMyXpHistories_returnsNextPage() {
        User user =
                arrangeActiveUser();

        XpHistoryCursor requestCursor =
                new XpHistoryCursor(
                        Instant.parse(
                                "2026-09-17T03:00:00Z"
                        ),
                        UUID.fromString(
                                "ffffffff-ffff-ffff-ffff-ffffffffffff"
                        )
                );

        String encodedCursor =
                cursorCodec.encode(
                        requestCursor
                );

        XpHistory history =
                mockHistory(
                        UUID.fromString(
                                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
                        ),
                        Instant.parse(
                                "2026-09-17T02:00:00Z"
                        ),
                        20L
                );

        when(
                xpHistoryRepository
                        .findNextPageByUserId(
                                USER_ID,
                                requestCursor.earnedAt(),
                                requestCursor.xpHistoryId(),
                                3
                        )
        )
                .thenReturn(
                        List.of(
                                history
                        )
                );

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                new GetMyXpHistoriesQuery(
                                        2,
                                        encodedCursor
                                )
                        );

        assertThat(result.items())
                .hasSize(1);

        assertThat(result.hasNext())
                .isFalse();

        assertThat(result.nextCursor())
                .isNull();

        verify(user)
                .assertActive();

        verify(xpHistoryRepository)
                .findNextPageByUserId(
                        USER_ID,
                        requestCursor.earnedAt(),
                        requestCursor.xpHistoryId(),
                        3
                );

        verify(
                xpHistoryRepository,
                never()
        )
                .findFirstPageByUserId(
                        USER_ID,
                        3
                );
    }

    @Test
    @DisplayName(
            "조회 결과가 size와 같으면 마지막 페이지로 처리한다"
    )
    void getMyXpHistories_returnsLastFullPage() {
        arrangeActiveUser();

        XpHistory first =
                mockHistory(
                        UUID.randomUUID(),
                        Instant.parse(
                                "2026-09-17T02:00:00Z"
                        ),
                        20L
                );

        XpHistory second =
                mockHistory(
                        UUID.randomUUID(),
                        Instant.parse(
                                "2026-09-17T01:00:00Z"
                        ),
                        10L
                );

        when(
                xpHistoryRepository
                        .findFirstPageByUserId(
                                USER_ID,
                                3
                        )
        )
                .thenReturn(
                        List.of(
                                first,
                                second
                        )
                );

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                new GetMyXpHistoriesQuery(
                                        2,
                                        null
                                )
                        );

        assertThat(result.items())
                .hasSize(2);

        assertThat(result.hasNext())
                .isFalse();

        assertThat(result.nextCursor())
                .isNull();
    }

    @Test
    @DisplayName(
            "XP 이력이 없으면 빈 마지막 페이지를 반환한다"
    )
    void getMyXpHistories_returnsEmptyPage() {
        arrangeActiveUser();

        when(
                xpHistoryRepository
                        .findFirstPageByUserId(
                                USER_ID,
                                21
                        )
        )
                .thenReturn(
                        List.of()
                );

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
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
            "사용자가 존재하지 않으면 USER_NOT_FOUND를 반환한다"
    )
    void getMyXpHistories_rejectsMissingUser() {
        when(
                userRepository.findById(
                        USER_ID
                )
        )
                .thenReturn(
                        Optional.empty()
                );

        assertThatThrownBy(
                () ->
                        getMyXpHistoriesService
                                .getMyXpHistories(
                                        USER_ID,
                                        GetMyXpHistoriesQuery.of(
                                                null,
                                                null
                                        )
                                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.USER_NOT_FOUND
                                )
                );

        verifyNoInteractions(
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName(
            "활성 상태가 아닌 사용자는 USER_NOT_ACTIVE를 반환한다"
    )
    void getMyXpHistories_rejectsInactiveUser() {
        User user =
                mock(
                        User.class
                );

        when(
                userRepository.findById(
                        USER_ID
                )
        )
                .thenReturn(
                        Optional.of(
                                user
                        )
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_NOT_ACTIVE
                )
        )
                .when(user)
                .assertActive();

        assertThatThrownBy(
                () ->
                        getMyXpHistoriesService
                                .getMyXpHistories(
                                        USER_ID,
                                        GetMyXpHistoriesQuery.of(
                                                null,
                                                null
                                        )
                                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.USER_NOT_ACTIVE
                                )
                );

        verifyNoInteractions(
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName(
            "잘못된 cursor는 사용자 및 XP 조회 전에 거부한다"
    )
    void getMyXpHistories_rejectsInvalidCursorBeforeRepositories() {
        GetMyXpHistoriesQuery query =
                new GetMyXpHistoriesQuery(
                        20,
                        "invalid"
                );

        assertThatThrownBy(
                () ->
                        getMyXpHistoriesService
                                .getMyXpHistories(
                                        USER_ID,
                                        query
                                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.INVALID_INPUT_VALUE
                                )
                );

        verifyNoInteractions(
                userRepository,
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName(
            "사용자 식별자가 null이면 조회하지 않는다"
    )
    void getMyXpHistories_rejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                getMyXpHistoriesService
                                        .getMyXpHistories(
                                                null,
                                                GetMyXpHistoriesQuery.of(
                                                        null,
                                                        null
                                                )
                                        )
                )
                .withMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName(
            "조회 조건이 null이면 조회하지 않는다"
    )
    void getMyXpHistories_rejectsNullQuery() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                getMyXpHistoriesService
                                        .getMyXpHistories(
                                                USER_ID,
                                                null
                                        )
                )
                .withMessage(
                        "XP 이력 조회 조건은 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                xpHistoryRepository
        );
    }

    private User arrangeActiveUser() {
        User user =
                mock(
                        User.class
                );

        when(
                userRepository.findById(
                        USER_ID
                )
        )
                .thenReturn(
                        Optional.of(
                                user
                        )
                );

        return user;
    }

    private XpHistory mockHistory(
            UUID id,
            Instant earnedAt,
            long balanceAfter
    ) {
        XpHistory history =
                mock(
                        XpHistory.class
                );

        when(history.getId())
                .thenReturn(id);

        when(history.getRewardType())
                .thenReturn(
                        RewardType.FIRST_CORRECT
                );

        when(history.getAmount())
                .thenReturn(10);

        when(history.getBalanceAfter())
                .thenReturn(balanceAfter);

        when(history.getDescription())
                .thenReturn(
                        "문제 최초 정답 보상"
                );

        when(history.getEarnedAt())
                .thenReturn(earnedAt);

        return history;
    }
}
