package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateMyProfileResultTest {

    private static final String EMAIL =
            "learner@example.com";

    @Test
    @DisplayName(
            "사용자 엔티티와 복호화된 이메일로 수정 결과를 생성한다"
    )
    void from_mapsUserProfile() {
        // given
        UUID userId =
                UUID.fromString(
                        "12345678-1234-5678-1234-123456789123"
                );

        Instant createdAt =
                Instant.parse(
                        "2026-09-15T01:00:00Z"
                );

        User user = mock(User.class);

        when(user.getId())
                .thenReturn(userId);

        when(user.getNickname())
                .thenReturn("김티암");

        when(user.getRole())
                .thenReturn(UserRole.USER);

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(user.getLearningLevel())
                .thenReturn(LearningLevel.BEGINNER);

        when(user.getJavaExperienceMonths())
                .thenReturn(3);

        when(user.getCreatedAt())
                .thenReturn(createdAt);

        // when
        UpdateMyProfileResult result =
                UpdateMyProfileResult.from(
                        user,
                        EMAIL
                );

        // then
        assertThat(result.userId())
                .isEqualTo(userId);

        assertThat(result.email())
                .isEqualTo(EMAIL);

        assertThat(result.nickname())
                .isEqualTo("김티암");

        assertThat(result.role())
                .isEqualTo(UserRole.USER);

        assertThat(result.status())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(result.learningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        assertThat(result.javaExperienceMonths())
                .isEqualTo(3);

        assertThat(result.createdAt())
                .isEqualTo(createdAt);
    }

    @Test
    @DisplayName(
            "toString은 복호화된 이메일 원문을 노출하지 않는다"
    )
    void toString_masksEmail() {
        // given
        UpdateMyProfileResult result =
                new UpdateMyProfileResult(
                        UUID.fromString(
                                "12345678-1234-5678-1234-123456789123"
                        ),
                        EMAIL,
                        "김티암",
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        LearningLevel.BEGINNER,
                        3,
                        Instant.parse(
                                "2026-09-15T01:00:00Z"
                        )
                );

        // when
        String value = result.toString();

        // then
        assertThat(value)
                .doesNotContain(EMAIL)
                .contains("[PROTECTED]");
    }
}
