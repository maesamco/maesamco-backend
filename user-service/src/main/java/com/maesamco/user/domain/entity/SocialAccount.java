package com.maesamco.user.domain.entity;

import com.maesamco.user.global.common.BaseEntity;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 외부 소셜 계정과 MAESAMCO User를 연결하는 도메인 엔티티입니다.
 *
 * <p>소셜 계정은 이메일이 아니라
 * {@code provider + providerUserId} 조합으로 식별합니다.</p>
 *
 * <p>예를 들어 Google은 Google OIDC의 {@code sub} 값을
 * providerUserId로 사용합니다.</p>
 *
 * <p>동일 이메일의 일반 회원이 존재하더라도
 * SocialAccount를 자동으로 생성하여 연결해서는 안 됩니다.</p>
 */
@Getter
@Entity
@Table(
        name = "p_social_accounts",
        schema = "user_schema"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

    private static final int PROVIDER_USER_ID_MAX_LENGTH = 255;

    /**
     * 소셜 계정 식별자입니다.
     */
    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    /**
     * 연결된 MAESAMCO 사용자 식별자입니다.
     */
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

    /**
     * 외부 인증 제공자입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "provider",
            nullable = false,
            length = 20,
            updatable = false
    )
    private SocialProvider provider;

    /**
     * 인증 제공자가 보장하는 사용자 고유 식별자입니다.
     *
     * <p>Google에서는 OIDC sub 값을 사용합니다.</p>
     */
    @Column(
            name = "provider_user_id",
            nullable = false,
            length = PROVIDER_USER_ID_MAX_LENGTH,
            updatable = false
    )
    private String providerUserId;

    private SocialAccount(
            UUID id,
            UUID userId,
            SocialProvider provider,
            String providerUserId
    ) {
        this.id = requireNonNull(
                id,
                "소셜 계정 ID는 필수입니다."
        );

        this.userId = requireNonNull(
                userId,
                "사용자 ID는 필수입니다."
        );

        this.provider = requireNonNull(
                provider,
                "소셜 로그인 제공자는 필수입니다."
        );

        this.providerUserId =
                validateProviderUserId(providerUserId);
    }

    /**
     * 신규 SocialAccount를 생성합니다.
     */
    public static SocialAccount create(
            UUID userId,
            SocialProvider provider,
            String providerUserId
    ) {
        return new SocialAccount(
                UUID.randomUUID(),
                userId,
                provider,
                providerUserId
        );
    }

    private static String validateProviderUserId(
            String providerUserId
    ) {
        if (
                providerUserId == null
                        || providerUserId.isBlank()
        ) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "소셜 사용자 식별자는 필수입니다."
            );
        }

        String value = providerUserId.trim();

        if (value.length() > PROVIDER_USER_ID_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "소셜 사용자 식별자는 255자 이하여야 합니다."
            );
        }

        return value;
    }

    private static <T> T requireNonNull(
            T value,
            String message
    ) {
        if (value == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    message
            );
        }

        return value;
    }
}
