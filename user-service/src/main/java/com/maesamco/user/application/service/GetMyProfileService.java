package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 기본 정보 조회를 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class GetMyProfileService {

    private final UserRepository userRepository;
    private final EmailCipher emailCipher;

    /**
     * 인증된 사용자의 기본 정보를 조회합니다.
     *
     * <p>DB에 암호화되어 저장된 이메일은 조회 결과를 생성하기 전에
     * 복호화합니다. 비밀번호 해시와 인증 세션 정보는 반환하지 않습니다.</p>
     *
     * @param userId 인증된 사용자 식별자
     * @return 로그인 사용자의 기본 정보
     */
    @Transactional(readOnly = true)
    public GetMyProfileResult getMyProfile(UUID userId) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        User user = userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        String email = emailCipher.decrypt(
                user.getEncryptedEmail()
        );

        return GetMyProfileResult.from(
                user,
                email
        );
    }
}
