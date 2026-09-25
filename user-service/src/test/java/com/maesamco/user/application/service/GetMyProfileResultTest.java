package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetMyProfileResultTest {

    private static final String EMAIL =
            "learner@example.com";

    @Test
    @DisplayName(
            "toString은 복호화된 이메일 원문을 노출하지 않는다"
    )
    void toString_masksEmail() {
        // given
        GetMyProfileResult result =
                new GetMyProfileResult(
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
                        ),
                        true
                );

        // when
        String value = result.toString();

        // then
        assertThat(value)
                .doesNotContain(EMAIL)
                .contains("[PROTECTED]");
    }
}
