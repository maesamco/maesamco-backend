package com.maesamco.user.infrastructure.security.jwt;

import com.maesamco.user.application.port.RefreshTokenHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sha256RefreshTokenHasher의 해시와 검증 규칙을 검증합니다.
 */
class Sha256RefreshTokenHasherTest {

    private static final String REFRESH_TOKEN =
            "refresh-token-value";

    private final RefreshTokenHasher refreshTokenHasher =
            new Sha256RefreshTokenHasher();

    @Test
    @DisplayName("Refresh Token을 SHA-256 해시로 변환한다")
    void hash_createsSha256Hash() {
        // when
        String firstHash =
                refreshTokenHasher.hash(REFRESH_TOKEN);

        String secondHash =
                refreshTokenHasher.hash(REFRESH_TOKEN);

        // then
        assertThat(firstHash)
                .isEqualTo(secondHash)
                .isNotEqualTo(REFRESH_TOKEN)
                .matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("Refresh Token이 저장된 해시와 일치하는지 확인한다")
    void matches_returnsCorrectResult() {
        // given
        String refreshTokenHash =
                refreshTokenHasher.hash(REFRESH_TOKEN);

        // then
        assertThat(refreshTokenHasher.matches(
                REFRESH_TOKEN,
                refreshTokenHash
        )).isTrue();

        assertThat(refreshTokenHasher.matches(
                "different-refresh-token",
                refreshTokenHash
        )).isFalse();
    }

    @Test
    @DisplayName("해시할 Refresh Token이 없으면 예외가 발생한다")
    void hash_rejectsNullOrBlankToken() {
        assertThatThrownBy(() ->
                refreshTokenHasher.hash(null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "해시할 Refresh Token은 필수입니다."
                );

        assertThatThrownBy(() ->
                refreshTokenHasher.hash("   ")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "해시할 Refresh Token은 필수입니다."
                );
    }

    @Test
    @DisplayName("검증할 Refresh Token이나 해시가 유효하지 않으면 false를 반환한다")
    void matches_returnsFalseForInvalidInput() {
        String refreshTokenHash =
                refreshTokenHasher.hash(REFRESH_TOKEN);

        assertThat(refreshTokenHasher.matches(
                null,
                refreshTokenHash
        )).isFalse();

        assertThat(refreshTokenHasher.matches(
                "   ",
                refreshTokenHash
        )).isFalse();

        assertThat(refreshTokenHasher.matches(
                REFRESH_TOKEN,
                null
        )).isFalse();

        assertThat(refreshTokenHasher.matches(
                REFRESH_TOKEN,
                "   "
        )).isFalse();

        assertThat(refreshTokenHasher.matches(
                REFRESH_TOKEN,
                "invalid*hash"
        )).isFalse();
    }
}
