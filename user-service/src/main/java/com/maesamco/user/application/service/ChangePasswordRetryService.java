package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 비밀번호 변경 중 낙관적 락 충돌이 발생하면
 * 한 번 재시도합니다.
 */
@Service
@RequiredArgsConstructor
public class ChangePasswordRetryService {

    private final ChangePasswordService changePasswordService;

    /**
     * 비밀번호 변경을 수행하고
     * 낙관적 락 충돌 시 한 번 재시도합니다.
     *
     * <p>재시도에서도 충돌하면 409 Conflict에 해당하는
     * 비즈니스 예외로 변환합니다.</p>
     *
     * @param userId 사용자 식별자
     * @param command 비밀번호 변경 명령
     */
    public void changePassword(
            UUID userId,
            ChangePasswordCommand command
    ) {
        try {
            changePasswordService.changePassword(
                    userId,
                    command
            );
        } catch (OptimisticLockingFailureException firstException) {
            retryChangePassword(
                    userId,
                    command
            );
        }
    }

    private void retryChangePassword(
            UUID userId,
            ChangePasswordCommand command
    ) {
        try {
            changePasswordService.changePassword(
                    userId,
                    command
            );
        } catch (OptimisticLockingFailureException secondException) {
            throw new BusinessException(
                    ErrorCode.USER_PASSWORD_CHANGE_CONFLICT
            );
        }
    }
}
