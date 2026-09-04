package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 이메일을 조회·저장 처리 전에 일관된 형태로 정규화합니다.
 *
 * <p>앞뒤 공백을 제거하고 로케일 영향을 받지 않도록
 * {@link Locale#ROOT}를 사용해 소문자로 변환합니다.</p>
 */
@Component
public class EmailNormalizer {

    /**
     * 입력 이메일을 정규화합니다.
     *
     * @param email 정규화할 이메일
     * @return 앞뒤 공백이 제거되고 소문자로 변환된 이메일
     */
    public String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "이메일은 필수입니다."
            );
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }
}
