package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자 기본 정보 수정 결과입니다.
 *
 * <p>비밀번호 해시와 인증 토큰 등 민감한 인증 정보는
 * 수정 결과에 포함하지 않습니다.</p>
 *
 * @param userId 사용자 식별자
 * @param email 복호화된 사용자 이메일
 * @param nickname 사용자 닉네임
 * @param role 사용자 권한
 * @param status 사용자 계정 상태
 * @param learningLevel Java 학습 수준
 * @param javaExperienceMonths Java 학습 경험 개월 수
 * @param createdAt 사용자 가입 일시
 */
@Schema(
        name = "UpdateMyProfileResult",
        description = "수정된 로그인 사용자의 기본 정보"
)
public record UpdateMyProfileResult(

        @Schema(
                description = "사용자 식별자",
                example = "12345678-1234-5678-1234-123456789123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID userId,

        @Schema(
                description = "사용자 이메일",
                example = "learner@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String email,

        @Schema(
                description = "사용자 닉네임",
                example = "김티암",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String nickname,

        @Schema(
                description = "사용자 권한",
                example = "USER",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UserRole role,

        @Schema(
                description = "사용자 계정 상태",
                example = "ACTIVE",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UserStatus status,

        @Schema(
                description = "Java 학습 수준",
                example = "BEGINNER",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        LearningLevel learningLevel,

        @Schema(
                description = "Java 학습 경험 개월 수",
                example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int javaExperienceMonths,

        @Schema(
                description = "사용자 가입 일시",
                example = "2026-09-15T01:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant createdAt
) {

    /**
     * 수정 결과의 필수값과 학습 경험 개월 수를 검증합니다.
     */
    public UpdateMyProfileResult {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                email,
                "사용자 이메일은 필수입니다."
        );
        Objects.requireNonNull(
                nickname,
                "사용자 닉네임은 필수입니다."
        );
        Objects.requireNonNull(
                role,
                "사용자 권한은 필수입니다."
        );
        Objects.requireNonNull(
                status,
                "사용자 상태는 필수입니다."
        );
        Objects.requireNonNull(
                learningLevel,
                "학습 수준은 필수입니다."
        );
        Objects.requireNonNull(
                createdAt,
                "사용자 가입 일시는 필수입니다."
        );

        if (javaExperienceMonths < 0) {
            throw new IllegalArgumentException(
                    "Java 경험 개월 수는 0 이상이어야 합니다."
            );
        }
    }

    /**
     * 사용자 엔티티와 복호화된 이메일로 수정 결과를 생성합니다.
     *
     * @param user 수정이 완료된 사용자
     * @param email 복호화된 사용자 이메일
     * @return 사용자 기본 정보 수정 결과
     */
    public static UpdateMyProfileResult from(
            User user,
            String email
    ) {
        Objects.requireNonNull(
                user,
                "사용자는 필수입니다."
        );

        return new UpdateMyProfileResult(
                user.getId(),
                email,
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getLearningLevel(),
                user.getJavaExperienceMonths(),
                user.getCreatedAt()
        );
    }

    /**
     * 로그 등에 복호화된 이메일 원문이 노출되지 않도록 마스킹합니다.
     */
    @Override
    public String toString() {
        return "UpdateMyProfileResult["
                + "userId=" + userId
                + ", email=[PROTECTED]"
                + ", nickname=" + nickname
                + ", role=" + role
                + ", status=" + status
                + ", learningLevel=" + learningLevel
                + ", javaExperienceMonths=" + javaExperienceMonths
                + ", createdAt=" + createdAt
                + "]";
    }
}
