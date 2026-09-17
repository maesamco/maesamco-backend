package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 XP 이력 조회를 처리합니다.
 *
 * <p>계속 누적되는 이력을 offset 없이 안정적으로 순회하기 위해
 * {@code (earnedAt, id)} 기반 keyset pagination을 사용합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class GetMyXpHistoriesService {

    private final UserRepository userRepository;

    private final XpHistoryRepository xpHistoryRepository;

    private final XpHistoryCursorCodec cursorCodec;

    /**
     * 인증된 활성 사용자의 XP 이력을 keyset pagination으로 조회합니다.
     *
     * @param userId 인증된 사용자 식별자
     * @param query 조회 크기와 cursor
     * @return XP 이력 페이지
     */
    @Transactional(readOnly = true)
    public GetMyXpHistoriesResult getMyXpHistories(
            UUID userId,
            GetMyXpHistoriesQuery query
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                query,
                "XP 이력 조회 조건은 필수입니다."
        );

        XpHistoryCursor cursor =
                decodeCursor(
                        query
                );

        User user =
                userRepository
                        .findById(
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.USER_NOT_FOUND
                                        )
                        );

        user.assertActive();

        int fetchLimit =
                query.size() + 1;

        List<XpHistory> fetchedHistories =
                cursor == null
                        ? xpHistoryRepository
                        .findFirstPageByUserId(
                                userId,
                                fetchLimit
                        )
                        : xpHistoryRepository
                        .findNextPageByUserId(
                                userId,
                                cursor.earnedAt(),
                                cursor.xpHistoryId(),
                                fetchLimit
                        );

        boolean hasNext =
                fetchedHistories.size()
                        > query.size();

        int responseSize =
                Math.min(
                        query.size(),
                        fetchedHistories.size()
                );

        List<XpHistory> pageHistories =
                fetchedHistories.subList(
                        0,
                        responseSize
                );

        List<GetMyXpHistoryItemResult> items =
                pageHistories.stream()
                        .map(
                                GetMyXpHistoryItemResult::from
                        )
                        .toList();

        String nextCursor =
                createNextCursor(
                        pageHistories,
                        hasNext
                );

        return new GetMyXpHistoriesResult(
                items,
                nextCursor,
                hasNext
        );
    }

    private XpHistoryCursor decodeCursor(
            GetMyXpHistoriesQuery query
    ) {
        if (!query.hasCursor()) {
            return null;
        }

        return cursorCodec.decode(
                query.cursor()
        );
    }

    private String createNextCursor(
            List<XpHistory> pageHistories,
            boolean hasNext
    ) {
        if (!hasNext) {
            return null;
        }

        XpHistory lastHistory =
                pageHistories.getLast();

        return cursorCodec.encode(
                new XpHistoryCursor(
                        lastHistory.getEarnedAt(),
                        lastHistory.getId()
                )
        );
    }
}
