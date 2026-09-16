package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInternalUserServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID FIRST_CONCEPT_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID SECOND_CONCEPT_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInterestConceptRepository
            userInterestConceptRepository;

    @Mock
    private User user;

    @InjectMocks
    private GetInternalUserService getInternalUserService;

    @Test
    @DisplayName(
            "활성 사용자의 관심 개념 식별자를 "
                    + "오름차순으로 조회한다"
    )
    void getInternalUser_returnsSortedInterestConceptIds() {
        // given
        UserInterestConcept secondInterest =
                UserInterestConcept.create(
                        USER_ID,
                        SECOND_CONCEPT_ID
                );

        UserInterestConcept firstInterest =
                UserInterestConcept.create(
                        USER_ID,
                        FIRST_CONCEPT_ID
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
                );

        when(user.getId())
                .thenReturn(USER_ID);

        when(
                userInterestConceptRepository.findAllByUserId(
                        USER_ID
                )
        ).thenReturn(
                List.of(
                        secondInterest,
                        firstInterest
                )
        );

        // when
        GetInternalUserResult result =
                getInternalUserService.getInternalUser(
                        USER_ID
                );

        // then
        assertThat(
                result.interestConceptIds()
        ).containsExactly(
                FIRST_CONCEPT_ID,
                SECOND_CONCEPT_ID
        );

        verify(userRepository)
                .findById(USER_ID);

        verify(user)
                .assertActive();

        verify(user)
                .getId();

        verify(userInterestConceptRepository)
                .findAllByUserId(USER_ID);
    }

    @Test
    @DisplayName(
            "활성 사용자에게 관심 개념이 없으면 "
                    + "빈 목록을 반환한다"
    )
    void getInternalUser_returnsEmptyInterestConceptIds() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
                );

        when(user.getId())
                .thenReturn(USER_ID);

        when(
                userInterestConceptRepository.findAllByUserId(
                        USER_ID
                )
        ).thenReturn(
                List.of()
        );

        // when
        GetInternalUserResult result =
                getInternalUserService.getInternalUser(
                        USER_ID
                );

        // then
        assertThat(
                result.interestConceptIds()
        ).isEmpty();

        verify(userRepository)
                .findById(USER_ID);

        verify(user)
                .assertActive();

        verify(user)
                .getId();

        verify(userInterestConceptRepository)
                .findAllByUserId(USER_ID);
    }

    @Test
    @DisplayName(
            "사용자가 존재하지 않으면 "
                    + "USER_NOT_FOUND를 반환한다"
    )
    void getInternalUser_rejectsMissingUser() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.empty()
                );

        // when & then
        assertBusinessError(
                () -> getInternalUserService.getInternalUser(
                        USER_ID
                ),
                ErrorCode.USER_NOT_FOUND
        );

        verify(userRepository)
                .findById(USER_ID);

        verifyNoInteractions(
                user,
                userInterestConceptRepository
        );
    }

    @Test
    @DisplayName(
            "활성 상태가 아닌 사용자는 "
                    + "USER_NOT_ACTIVE를 반환한다"
    )
    void getInternalUser_rejectsInactiveUser() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_NOT_ACTIVE
                )
        ).when(user)
                .assertActive();

        // when & then
        assertBusinessError(
                () -> getInternalUserService.getInternalUser(
                        USER_ID
                ),
                ErrorCode.USER_NOT_ACTIVE
        );

        verify(userRepository)
                .findById(USER_ID);

        verify(user)
                .assertActive();

        verifyNoInteractions(
                userInterestConceptRepository
        );
    }

    @Test
    @DisplayName(
            "사용자 식별자가 null이면 "
                    + "조회 작업을 수행하지 않는다"
    )
    void getInternalUser_rejectsNullUserId() {
        // when & then
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                getInternalUserService
                                        .getInternalUser(
                                                null
                                        )
                );

        verifyNoInteractions(
                userRepository,
                userInterestConceptRepository,
                user
        );
    }

    private void assertBusinessError(
            Runnable executable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(
                executable::run
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        expectedErrorCode
                                )
                );
    }
}