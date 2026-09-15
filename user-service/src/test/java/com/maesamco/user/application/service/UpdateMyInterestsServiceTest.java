package com.maesamco.user.application.service;

import com.maesamco.user.application.port.ConceptValidationPort;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * 관심 개념 전체 교체 서비스의 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class UpdateMyInterestsServiceTest {

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

    private static final UUID THIRD_CONCEPT_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInterestConceptRepository
            interestConceptRepository;

    @Mock
    private ConceptValidationPort conceptValidationPort;

    @Mock
    private User user;

    @InjectMocks
    private UpdateMyInterestsService
            updateMyInterestsService;

    @Test
    @DisplayName(
            "기존 목록과 요청 목록의 차이를 계산하여 제거하고 추가한다"
    )
    void updateMyInterests() {
        UserInterestConcept removedInterest =
                UserInterestConcept.create(
                        USER_ID,
                        FIRST_CONCEPT_ID
                );

        UserInterestConcept retainedInterest =
                UserInterestConcept.create(
                        USER_ID,
                        SECOND_CONCEPT_ID
                );

        stubActiveUser();
        stubLockedActiveUser();

        when(
                interestConceptRepository.findAllByUserId(
                        USER_ID
                )
        ).thenReturn(
                List.of(
                        removedInterest,
                        retainedInterest
                )
        );

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of(
                                SECOND_CONCEPT_ID,
                                THIRD_CONCEPT_ID
                        )
                );

        UpdateMyInterestsResult result =
                updateMyInterestsService.updateMyInterests(
                        USER_ID,
                        command
                );

        verify(conceptValidationPort)
                .validateAll(
                        List.of(
                                SECOND_CONCEPT_ID,
                                THIRD_CONCEPT_ID
                        )
                );

        verify(interestConceptRepository)
                .saveAllAndFlush(
                        argThat(
                                interests ->
                                        interests.size() == 1
                                                && interests.getFirst()
                                                == removedInterest
                                                && removedInterest.isDeleted()
                        )
                );

        verify(interestConceptRepository)
                .saveAllAndFlush(
                        argThat(
                                interests ->
                                        interests.size() == 1
                                                && interests.getFirst()
                                                .getConceptId()
                                                .equals(
                                                        THIRD_CONCEPT_ID
                                                )
                        )
                );

        assertThat(removedInterest.isDeleted())
                .isTrue();

        assertThat(retainedInterest.isDeleted())
                .isFalse();

        assertThat(result.interestConceptIds())
                .containsExactly(
                        SECOND_CONCEPT_ID,
                        THIRD_CONCEPT_ID
                );

        assertThat(result.count())
                .isEqualTo(2);

        assertThat(result.updatedAt())
                .isNotNull();
    }

    @Test
    @DisplayName(
            "빈 목록을 요청하면 외부 검증 없이 모든 관심 개념을 해제한다"
    )
    void clearAllInterests() {
        UserInterestConcept firstInterest =
                UserInterestConcept.create(
                        USER_ID,
                        FIRST_CONCEPT_ID
                );

        UserInterestConcept secondInterest =
                UserInterestConcept.create(
                        USER_ID,
                        SECOND_CONCEPT_ID
                );

        stubActiveUser();
        stubLockedActiveUser();

        when(
                interestConceptRepository.findAllByUserId(
                        USER_ID
                )
        ).thenReturn(
                List.of(
                        firstInterest,
                        secondInterest
                )
        );

        UpdateMyInterestsResult result =
                updateMyInterestsService.updateMyInterests(
                        USER_ID,
                        new UpdateMyInterestsCommand(
                                List.of()
                        )
                );

        verifyNoInteractions(
                conceptValidationPort
        );

        verify(interestConceptRepository)
                .saveAllAndFlush(
                        argThat(
                                interests ->
                                        interests.size() == 2
                                                && interests
                                                .stream()
                                                .allMatch(
                                                        UserInterestConcept
                                                                ::isDeleted
                                                )
                        )
                );

        assertThat(result.interestConceptIds())
                .isEmpty();

        assertThat(result.count())
                .isZero();
    }

    @Test
    @DisplayName(
            "동일한 관심 개념 목록을 요청하면 중복 저장하지 않는다"
    )
    void sameInterestsAreIdempotent() {
        UserInterestConcept firstInterest =
                UserInterestConcept.create(
                        USER_ID,
                        FIRST_CONCEPT_ID
                );

        UserInterestConcept secondInterest =
                UserInterestConcept.create(
                        USER_ID,
                        SECOND_CONCEPT_ID
                );

        stubActiveUser();
        stubLockedActiveUser();

        when(
                interestConceptRepository.findAllByUserId(
                        USER_ID
                )
        ).thenReturn(
                List.of(
                        firstInterest,
                        secondInterest
                )
        );

        UpdateMyInterestsResult result =
                updateMyInterestsService.updateMyInterests(
                        USER_ID,
                        new UpdateMyInterestsCommand(
                                List.of(
                                        SECOND_CONCEPT_ID,
                                        FIRST_CONCEPT_ID
                                )
                        )
                );

        verify(conceptValidationPort)
                .validateAll(
                        List.of(
                                SECOND_CONCEPT_ID,
                                FIRST_CONCEPT_ID
                        )
                );

        verify(
                interestConceptRepository,
                never()
        ).saveAllAndFlush(
                org.mockito.ArgumentMatchers.anyList()
        );

        assertThat(result.interestConceptIds())
                .containsExactly(
                        SECOND_CONCEPT_ID,
                        FIRST_CONCEPT_ID
                );

        assertThat(result.count())
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Content Service 검증 실패 시 기존 관심 개념을 변경하지 않는다"
    )
    void validationFailureKeepsCurrentInterests() {
        stubActiveUser();

        List<UUID> conceptIds =
                List.of(FIRST_CONCEPT_ID);

        doThrow(
                new BusinessException(
                        ErrorCode.CONCEPT_NOT_FOUND
                )
        ).when(
                conceptValidationPort
        ).validateAll(conceptIds);

        assertBusinessError(
                () -> updateMyInterestsService
                        .updateMyInterests(
                                USER_ID,
                                new UpdateMyInterestsCommand(
                                        conceptIds
                                )
                        ),
                ErrorCode.CONCEPT_NOT_FOUND
        );

        verifyNoInteractions(
                interestConceptRepository
        );
    }

    @Test
    @DisplayName(
            "사용자가 존재하지 않으면 USER_NOT_FOUND를 반환한다"
    )
    void rejectsMissingUser() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        assertBusinessError(
                () -> updateMyInterestsService
                        .updateMyInterests(
                                USER_ID,
                                new UpdateMyInterestsCommand(
                                        List.of()
                                )
                        ),
                ErrorCode.USER_NOT_FOUND
        );

        verifyNoInteractions(
                conceptValidationPort,
                interestConceptRepository
        );
    }

    @Test
    @DisplayName(
            "활성 상태가 아닌 사용자는 USER_NOT_ACTIVE를 반환한다"
    )
    void rejectsInactiveUser() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getStatus())
                .thenReturn(UserStatus.SUSPENDED);

        assertBusinessError(
                () -> updateMyInterestsService
                        .updateMyInterests(
                                USER_ID,
                                new UpdateMyInterestsCommand(
                                        List.of(
                                                FIRST_CONCEPT_ID
                                        )
                                )
                        ),
                ErrorCode.USER_NOT_ACTIVE
        );

        verifyNoInteractions(
                conceptValidationPort,
                interestConceptRepository
        );
    }

    @Test
    @DisplayName(
            "사용자 식별자가 null이면 교체 작업을 수행하지 않는다"
    )
    void rejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> updateMyInterestsService
                                .updateMyInterests(
                                        null,
                                        new UpdateMyInterestsCommand(
                                                List.of()
                                        )
                                )
                )
                .withMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                conceptValidationPort,
                interestConceptRepository
        );
    }

    @Test
    @DisplayName(
            "관심 개념 수정 명령이 null이면 교체 작업을 수행하지 않는다"
    )
    void rejectsNullCommand() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> updateMyInterestsService
                                .updateMyInterests(
                                        USER_ID,
                                        null
                                )
                )
                .withMessage(
                        "관심 개념 수정 명령은 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                conceptValidationPort,
                interestConceptRepository
        );
    }

    private void stubActiveUser() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);
    }

    private void assertBusinessError(
            ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(expectedErrorCode);
    }

    /**
     * 사용자 행 잠금 조회에서도 활성 사용자를 반환하도록 설정합니다.
     */
    private void stubLockedActiveUser() {
        when(
                userRepository.findByIdForUpdate(USER_ID)
        ).thenReturn(
                Optional.of(user)
        );
    }
}
