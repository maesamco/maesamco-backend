package com.maesamco.user.application.service;

import com.maesamco.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetQuizTargetUsersServiceTest {

    private static final UUID CURSOR =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID FIRST_USER_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID SECOND_USER_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID LOOKAHEAD_USER_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetQuizTargetUsersService getQuizTargetUsersService;

    @Test
    @DisplayName(
            "첫 페이지에서 요청 크기보다 한 건 더 조회하여 "
                    + "다음 페이지 존재 여부를 판단한다"
    )
    void getQuizTargetUsers_returnsFirstPageWithNextCursor() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        null,
                        2
                );

        when(
                userRepository.findQuizTargetUserIds(
                        null,
                        3
                )
        ).thenReturn(
                List.of(
                        FIRST_USER_ID,
                        SECOND_USER_ID,
                        LOOKAHEAD_USER_ID
                )
        );

        // when
        GetQuizTargetUsersResult result =
                getQuizTargetUsersService.getQuizTargetUsers(
                        query
                );

        // then
        assertThat(
                result.userIds()
        ).containsExactly(
                FIRST_USER_ID,
                SECOND_USER_ID
        );

        assertThat(
                result.hasNext()
        ).isTrue();

        assertThat(
                result.nextCursor()
        ).isEqualTo(
                SECOND_USER_ID
        );

        verify(userRepository)
                .findQuizTargetUserIds(
                        null,
                        3
                );
    }

    @Test
    @DisplayName(
            "후속 페이지 조회 시 전달받은 커서를 "
                    + "Repository에 그대로 전달한다"
    )
    void getQuizTargetUsers_forwardsCursorToRepository() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        CURSOR,
                        2
                );

        when(
                userRepository.findQuizTargetUserIds(
                        CURSOR,
                        3
                )
        ).thenReturn(
                List.of(
                        FIRST_USER_ID,
                        SECOND_USER_ID,
                        LOOKAHEAD_USER_ID
                )
        );

        // when
        GetQuizTargetUsersResult result =
                getQuizTargetUsersService.getQuizTargetUsers(
                        query
                );

        // then
        assertThat(
                result.userIds()
        ).containsExactly(
                FIRST_USER_ID,
                SECOND_USER_ID
        );

        assertThat(
                result.hasNext()
        ).isTrue();

        assertThat(
                result.nextCursor()
        ).isEqualTo(
                SECOND_USER_ID
        );

        verify(userRepository)
                .findQuizTargetUserIds(
                        CURSOR,
                        3
                );
    }

    @Test
    @DisplayName(
            "조회 결과가 요청 크기와 같으면 "
                    + "마지막 페이지로 판단한다"
    )
    void getQuizTargetUsers_returnsLastFullPage() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        CURSOR,
                        2
                );

        when(
                userRepository.findQuizTargetUserIds(
                        CURSOR,
                        3
                )
        ).thenReturn(
                List.of(
                        FIRST_USER_ID,
                        SECOND_USER_ID
                )
        );

        // when
        GetQuizTargetUsersResult result =
                getQuizTargetUsersService.getQuizTargetUsers(
                        query
                );

        // then
        assertThat(
                result.userIds()
        ).containsExactly(
                FIRST_USER_ID,
                SECOND_USER_ID
        );

        assertThat(
                result.hasNext()
        ).isFalse();

        assertThat(
                result.nextCursor()
        ).isNull();

        verify(userRepository)
                .findQuizTargetUserIds(
                        CURSOR,
                        3
                );
    }

    @Test
    @DisplayName(
            "조회 결과가 비어 있으면 "
                    + "빈 마지막 페이지를 반환한다"
    )
    void getQuizTargetUsers_returnsEmptyLastPage() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        CURSOR,
                        10
                );

        when(
                userRepository.findQuizTargetUserIds(
                        CURSOR,
                        11
                )
        ).thenReturn(
                List.of()
        );

        // when
        GetQuizTargetUsersResult result =
                getQuizTargetUsersService.getQuizTargetUsers(
                        query
                );

        // then
        assertThat(
                result.userIds()
        ).isEmpty();

        assertThat(
                result.hasNext()
        ).isFalse();

        assertThat(
                result.nextCursor()
        ).isNull();

        verify(userRepository)
                .findQuizTargetUserIds(
                        CURSOR,
                        11
                );
    }

    @Test
    @DisplayName(
            "최소 요청 크기에서도 한 건을 추가 조회하여 "
                    + "다음 페이지를 판단한다"
    )
    void getQuizTargetUsers_supportsMinimumPageSize() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        null,
                        1
                );

        when(
                userRepository.findQuizTargetUserIds(
                        null,
                        2
                )
        ).thenReturn(
                List.of(
                        FIRST_USER_ID,
                        SECOND_USER_ID
                )
        );

        // when
        GetQuizTargetUsersResult result =
                getQuizTargetUsersService.getQuizTargetUsers(
                        query
                );

        // then
        assertThat(
                result.userIds()
        ).containsExactly(
                FIRST_USER_ID
        );

        assertThat(
                result.hasNext()
        ).isTrue();

        assertThat(
                result.nextCursor()
        ).isEqualTo(
                FIRST_USER_ID
        );

        verify(userRepository)
                .findQuizTargetUserIds(
                        null,
                        2
                );
    }

    @Test
    @DisplayName(
            "최대 요청 크기에서는 Repository에 "
                    + "1001건 조회를 요청한다"
    )
    void getQuizTargetUsers_requestsLookaheadAtMaximumSize() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        null,
                        1000
                );

        when(
                userRepository.findQuizTargetUserIds(
                        null,
                        1001
                )
        ).thenReturn(
                List.of()
        );

        // when
        GetQuizTargetUsersResult result =
                getQuizTargetUsersService.getQuizTargetUsers(
                        query
                );

        // then
        assertThat(
                result.userIds()
        ).isEmpty();

        assertThat(
                result.hasNext()
        ).isFalse();

        assertThat(
                result.nextCursor()
        ).isNull();

        verify(userRepository)
                .findQuizTargetUserIds(
                        null,
                        1001
                );
    }

    @Test
    @DisplayName(
            "조회 조건이 null이면 "
                    + "Repository를 호출하지 않는다"
    )
    void getQuizTargetUsers_rejectsNullQuery() {
        // when & then
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                getQuizTargetUsersService
                                        .getQuizTargetUsers(
                                                null
                                        )
                );

        verifyNoInteractions(
                userRepository
        );
    }

    @Test
    @DisplayName(
            "Repository가 null을 반환하면 "
                    + "잘못된 조회 결과로 거부한다"
    )
    void getQuizTargetUsers_rejectsNullRepositoryResult() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        CURSOR,
                        2
                );

        when(
                userRepository.findQuizTargetUserIds(
                        CURSOR,
                        3
                )
        ).thenReturn(
                null
        );

        // when & then
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                getQuizTargetUsersService
                                        .getQuizTargetUsers(
                                                query
                                        )
                );

        verify(userRepository)
                .findQuizTargetUserIds(
                        CURSOR,
                        3
                );
    }

    @Test
    @DisplayName(
            "Repository 조회 예외는 숨기지 않고 "
                    + "호출자에게 전파한다"
    )
    void getQuizTargetUsers_propagatesRepositoryException() {
        // given
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        CURSOR,
                        2
                );

        IllegalStateException repositoryException =
                new IllegalStateException(
                        "Repository 조회 실패"
                );

        when(
                userRepository.findQuizTargetUserIds(
                        CURSOR,
                        3
                )
        ).thenThrow(
                repositoryException
        );

        // when & then
        assertThatThrownBy(
                () ->
                        getQuizTargetUsersService
                                .getQuizTargetUsers(
                                        query
                                )
        ).isSameAs(
                repositoryException
        );

        verify(userRepository)
                .findQuizTargetUserIds(
                        CURSOR,
                        3
                );
    }
}