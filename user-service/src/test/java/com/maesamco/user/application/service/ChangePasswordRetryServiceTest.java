package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import com.maesamco.user.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangePasswordRetryServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final ChangePasswordCommand COMMAND =
            new ChangePasswordCommand(
                    "Abcd1234!",
                    "NewAbcd1234!"
            );

    @Mock
    private ChangePasswordService changePasswordService;

    @InjectMocks
    private ChangePasswordRetryService changePasswordRetryService;

    @Test
    @DisplayName(
            "낙관적 락 충돌이 한 번 발생하면 "
                    + "새 트랜잭션으로 한 번 재시도한다"
    )
    void changePassword_retriesOnceAfterOptimisticLockConflict() {
        // given
        doThrow(
                new OptimisticLockingFailureException(
                        "동시 수정 충돌"
                )
        )
                .doNothing()
                .when(changePasswordService)
                .changePassword(
                        USER_ID,
                        COMMAND
                );

        // when & then
        assertThatCode(
                () -> changePasswordRetryService.changePassword(
                        USER_ID,
                        COMMAND
                )
        ).doesNotThrowAnyException();

        verify(
                changePasswordService,
                times(2)
        ).changePassword(
                USER_ID,
                COMMAND
        );
    }

    @Test
    @DisplayName(
            "낙관적 락 충돌이 재시도에서도 발생하면 "
                    + "409 Conflict를 반환한다"
    )
    void changePassword_throwsConflictWhenRetryAlsoFails() {
        // given
        doThrow(
                new OptimisticLockingFailureException(
                        "첫 번째 동시 수정 충돌"
                )
        )
                .doThrow(
                        new OptimisticLockingFailureException(
                                "두 번째 동시 수정 충돌"
                        )
                )
                .when(changePasswordService)
                .changePassword(
                        USER_ID,
                        COMMAND
                );

        // when & then
        assertThatThrownBy(
                () -> changePasswordRetryService.changePassword(
                        USER_ID,
                        COMMAND
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception
                                                .getErrorCode()
                                                .getStatus()
                                ).isEqualTo(
                                        HttpStatus.CONFLICT
                                )
                );

        verify(
                changePasswordService,
                times(2)
        ).changePassword(
                USER_ID,
                COMMAND
        );
    }
}
