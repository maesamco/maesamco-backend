package com.maesamco.user.application.service;

import com.maesamco.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Content Service의 Daily Quiz 생성 대상 사용자를
 * UUID cursor 방식으로 조회합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetQuizTargetUsersService {

    /**
     * 다음 페이지 존재 여부를 확인하기 위해
     * 요청 크기보다 추가로 조회할 행의 수입니다.
     */
    private static final int LOOKAHEAD_SIZE = 1;

    private final UserRepository userRepository;

    /**
     * Daily Quiz 생성 대상 사용자 페이지를 조회합니다.
     *
     * <p>DB에서 요청 크기보다 한 건을 더 조회하여
     * 전체 개수 조회 없이 다음 페이지 존재 여부를 판단합니다.</p>
     *
     * @param query cursor와 조회 크기
     * @return 사용자 ID 목록과 다음 cursor 정보
     */
    public GetQuizTargetUsersResult getQuizTargetUsers(
            GetQuizTargetUsersQuery query
    ) {
        Objects.requireNonNull(
                query,
                "Daily Quiz 대상 사용자 조회 조건은 필수입니다."
        );

        int requestedSize =
                query.size();

        int fetchLimit =
                requestedSize
                        + LOOKAHEAD_SIZE;

        List<UUID> fetchedUserIds =
                Objects.requireNonNull(
                        userRepository
                                .findQuizTargetUserIds(
                                        query.cursor(),
                                        fetchLimit
                                ),
                        "Repository 조회 결과는 null일 수 없습니다."
                );

        boolean hasNext =
                fetchedUserIds.size()
                        > requestedSize;

        List<UUID> userIds =
                extractCurrentPage(
                        fetchedUserIds,
                        requestedSize,
                        hasNext
                );

        UUID nextCursor =
                hasNext
                        ? userIds.getLast()
                        : null;

        return new GetQuizTargetUsersResult(
                userIds,
                nextCursor,
                hasNext
        );
    }

    /**
     * lookahead 행을 제외하고 현재 페이지에 포함할 ID만 반환합니다.
     */
    private List<UUID> extractCurrentPage(
            List<UUID> fetchedUserIds,
            int requestedSize,
            boolean hasNext
    ) {
        if (!hasNext) {
            return List.copyOf(
                    fetchedUserIds
            );
        }

        return List.copyOf(
                fetchedUserIds.subList(
                        0,
                        requestedSize
                )
        );
    }
}
