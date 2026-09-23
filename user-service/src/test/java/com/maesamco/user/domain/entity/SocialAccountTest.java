package com.maesamco.user.domain.entity;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialAccountTest {

    @Test
    @DisplayName(
            "사용자 ID와 Provider 사용자 ID로 소셜 계정을 생성한다"
    )
    void create() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        SocialAccount socialAccount =
                SocialAccount.create(
                        userId,
                        SocialProvider.GOOGLE,
                        "google-sub-123"
                );

        // then
        assertThat(socialAccount.getId())
                .isNotNull();

        assertThat(socialAccount.getUserId())
                .isEqualTo(userId);

        assertThat(socialAccount.getProvider())
                .isEqualTo(SocialProvider.GOOGLE);

        assertThat(socialAccount.getProviderUserId())
                .isEqualTo("google-sub-123");
    }

    @Test
    @DisplayName(
            "Provider 사용자 ID의 앞뒤 공백은 제거한다"
    )
    void create_trimsProviderUserId() {
        // when
        SocialAccount socialAccount =
                SocialAccount.create(
                        UUID.randomUUID(),
                        SocialProvider.GOOGLE,
                        "  google-sub-123  "
                );

        // then
        assertThat(socialAccount.getProviderUserId())
                .isEqualTo("google-sub-123");
    }

    @Test
    @DisplayName(
            "사용자 ID가 없으면 소셜 계정 생성을 거부한다"
    )
    void create_rejectsNullUserId() {
        assertThatThrownBy(
                () -> SocialAccount.create(
                        null,
                        SocialProvider.GOOGLE,
                        "google-sub-123"
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName(
            "Provider가 없으면 소셜 계정 생성을 거부한다"
    )
    void create_rejectsNullProvider() {
        assertThatThrownBy(
                () -> SocialAccount.create(
                        UUID.randomUUID(),
                        null,
                        "google-sub-123"
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName(
            "Provider 사용자 ID가 null이면 소셜 계정 생성을 거부한다"
    )
    void create_rejectsNullProviderUserId() {
        assertThatThrownBy(
                () -> SocialAccount.create(
                        UUID.randomUUID(),
                        SocialProvider.GOOGLE,
                        null
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName(
            "Provider 사용자 ID가 공백이면 소셜 계정 생성을 거부한다"
    )
    void create_rejectsBlankProviderUserId() {
        assertThatThrownBy(
                () -> SocialAccount.create(
                        UUID.randomUUID(),
                        SocialProvider.GOOGLE,
                        "   "
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName(
            "Provider 사용자 ID가 255자를 초과하면 소셜 계정 생성을 거부한다"
    )
    void create_rejectsTooLongProviderUserId() {
        assertThatThrownBy(
                () -> SocialAccount.create(
                        UUID.randomUUID(),
                        SocialProvider.GOOGLE,
                        "a".repeat(256)
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName(
            "Provider 사용자 ID는 최대 255자까지 허용한다"
    )
    void create_allowsProviderUserIdLength255() {
        // given
        String providerUserId =
                "a".repeat(255);

        // when
        SocialAccount socialAccount =
                SocialAccount.create(
                        UUID.randomUUID(),
                        SocialProvider.GOOGLE,
                        providerUserId
                );

        // then
        assertThat(socialAccount.getProviderUserId())
                .hasSize(255);
    }
}
