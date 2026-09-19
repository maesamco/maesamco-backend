package com.maesamco.user.presentation.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthCookiePropertiesTest {

    @Test
    @DisplayName("SameSite와 Path 설정값의 공백을 제거하고 표준 표기로 정규화한다")
    void constructor_normalizesConfiguration() {
        AuthCookieProperties properties =
                new AuthCookieProperties(
                        true,
                        "  lAx  ",
                        "  /api/v1/auth  "
                );

        assertThat(properties.secure())
                .isTrue();
        assertThat(properties.sameSite())
                .isEqualTo("Lax");
        assertThat(properties.path())
                .isEqualTo("/api/v1/auth");
    }

    @Test
    @DisplayName("지원하지 않는 SameSite 설정값을 거부한다")
    void constructor_rejectsUnsupportedSameSite() {
        assertThatThrownBy(
                () -> new AuthCookieProperties(
                        true,
                        "invalid",
                        "/api/v1/auth"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Strict, Lax, or None"
                );
    }

    @Test
    @DisplayName("빈 SameSite 설정값을 거부한다")
    void constructor_rejectsBlankSameSite() {
        assertThatThrownBy(
                () -> new AuthCookieProperties(
                        true,
                        "   ",
                        "/api/v1/auth"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "same-site must not be blank"
                );
    }

    @Test
    @DisplayName("슬래시로 시작하지 않는 Cookie Path를 거부한다")
    void constructor_rejectsInvalidPath() {
        assertThatThrownBy(
                () -> new AuthCookieProperties(
                        true,
                        "Lax",
                        "api/v1/auth"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "path must start with '/'"
                );
    }

    @Test
    @DisplayName("Secure가 비활성화된 SameSite None 설정을 거부한다")
    void constructor_rejectsInsecureSameSiteNone() {
        assertThatThrownBy(
                () -> new AuthCookieProperties(
                        false,
                        "None",
                        "/api/v1/auth"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "requires a Secure cookie"
                );
    }
}
