package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
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
class GetMyGamificationServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserGamificationStateRepository
            userGamificationStateRepository;

    @Mock
    private User user;

    @Mock
    private UserGamificationState state;

    @InjectMocks
    private GetMyGamificationService getMyGamificationService;

    @Test
    @DisplayName(
            "활성 사용자의 게이미피케이션 상태를 반환한다"
    )
    void getMyGamification_returnsState() {
        LocalDate activityDate =
                LocalDate.of(
                        2026,
                        9,
                        16
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
                );

        when(
                userGamificationStateRepository
                        .findByUserId(USER_ID)
        )
                .thenReturn(
                        Optional.of(state)
                );

        when(state.getTotalXp())
                .thenReturn(120L);

        when(state.getLevel())
                .thenReturn(2);

        when(state.getCurrentStreak())
                .thenReturn(3);

        when(state.getLongestStreak())
                .thenReturn(7);

        when(state.getLastActivityDate())
                .thenReturn(activityDate);

        GetMyGamificationResult result =
                getMyGamificationService
                        .getMyGamification(
                                USER_ID
                        );

        assertThat(result.totalXp())
                .isEqualTo(120L);

        assertThat(result.level())
                .isEqualTo(2);

        assertThat(result.currentStreak())
                .isEqualTo(3);

        assertThat(result.longestStreak())
                .isEqualTo(7);

        assertThat(result.lastActivityDate())
                .isEqualTo(activityDate);

        verify(user)
                .assertActive();

        verify(
                userGamificationStateRepository
        )
                .findByUserId(USER_ID);
    }

    @Test
    @DisplayName(
            "사용자가 없으면 USER_NOT_FOUND를 반환한다"
    )
    void getMyGamification_rejectsUnknownUser() {
        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.empty()
                );

        assertThatThrownBy(
                () ->
                        getMyGamificationService
                                .getMyGamification(
                                        USER_ID
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

        verifyNoInteractions(
                userGamificationStateRepository
        );
    }

    @Test
    @DisplayName(
            "비활성 사용자는 USER_NOT_ACTIVE로 거부한다"
    )
    void getMyGamification_rejectsInactiveUser() {
        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
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
                        getMyGamificationService
                                .getMyGamification(
                                        USER_ID
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

        verifyNoInteractions(
                userGamificationStateRepository
        );
    }

    @Test
    @DisplayName(
            "게이미피케이션 상태가 없으면 GAMIFICATION_STATE_NOT_FOUND를 반환한다"
    )
    void getMyGamification_rejectsMissingState() {
        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.of(user)
                );

        when(
                userGamificationStateRepository
                        .findByUserId(USER_ID)
        )
                .thenReturn(
                        Optional.empty()
                );

        assertThatThrownBy(
                () ->
                        getMyGamificationService
                                .getMyGamification(
                                        USER_ID
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.GAMIFICATION_STATE_NOT_FOUND
                );

        verify(user)
                .assertActive();
    }

    @Test
    @DisplayName(
            "사용자 식별자가 null이면 Repository를 조회하지 않는다"
    )
    void getMyGamification_rejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                getMyGamificationService
                                        .getMyGamification(
                                                null
                                        )
                )
                .withMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                userGamificationStateRepository
        );
    }

    @Test
    @DisplayName(
            "게이미피케이션 조회는 readOnly 트랜잭션으로 실행한다"
    )
    void getMyGamification_isReadOnlyTransaction()
            throws NoSuchMethodException {

        Method method =
                GetMyGamificationService.class
                        .getMethod(
                                "getMyGamification",
                                UUID.class
                        );

        Transactional transactional =
                method.getAnnotation(
                        Transactional.class
                );

        assertThat(transactional)
                .isNotNull();

        assertThat(transactional.readOnly())
                .isTrue();
    }
}
