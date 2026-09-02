package com.maesamco.user.domain.entity;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자의 XP·레벨·스트릭 현재 상태를 관리하는 Aggregate Root입니다.
 *
 * <p>사용자마다 한 행만 존재하며 {@code userId}를 식별자로 사용합니다.
 * 여러 학습 완료 이벤트가 같은 행을 갱신할 수 있으므로 {@link Version}을
 * 이용한 낙관적 락으로 갱신 유실을 방지합니다.</p>
 *
 * <p>집계·상태형 테이블이므로 소프트 삭제와 행위자 감사 컬럼을 사용하지 않습니다.</p>
 *
 * <p>TODO(#29): Flyway 베이스라인에
 * {@code user_id -> p_users.id} 외래 키와 XP·레벨·스트릭의
 * 음수 방지 CHECK 제약이 반영됐는지 검증해야 합니다.</p>
 */
@Getter
@Entity
@Table(
        name = "p_user_gamification_states",
        schema = "user_schema"
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGamificationState {

    /**
     * 사용자 식별자이자 게이미피케이션 상태의 식별자입니다.
     */
    @Id
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

    /**
     * 사용자의 현재 누적 XP입니다.
     */
    @Column(name = "total_xp", nullable = false)
    private long totalXp;

    /**
     * 사용자의 현재 레벨입니다.
     */
    @Column(name = "level", nullable = false)
    private int level;

    /**
     * 현재 연속 학습 일수입니다.
     */
    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    /**
     * 사용자가 달성한 최장 연속 학습 일수입니다.
     */
    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    /**
     * Asia/Seoul 기준 마지막 유효 학습 날짜입니다.
     */
    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    /**
     * 동시 갱신 충돌을 감지하기 위한 낙관적 락 버전입니다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 게이미피케이션 상태가 마지막으로 갱신된 시각입니다.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 초기 상태로 사용자 게이미피케이션 객체를 생성합니다.
     */
    private UserGamificationState(UUID userId) {
        this.userId = requireNonNull(
                userId,
                "사용자 ID는 필수입니다."
        );
        this.totalXp = 0L;
        this.level = 1;
        this.currentStreak = 0;
        this.longestStreak = 0;
        this.lastActivityDate = null;
    }

    /**
     * 사용자의 초기 게이미피케이션 상태를 생성합니다.
     *
     * @param userId 사용자 식별자
     * @return 초기화된 사용자 게이미피케이션 상태
     */
    public static UserGamificationState create(UUID userId) {
        return new UserGamificationState(userId);
    }

    /**
     * XP 증감 결과와 계산된 레벨을 현재 상태에 반영합니다.
     *
     * <p>XP 지급 정책과 레벨 산식은 응용 계층의 정책 객체가 결정하고,
     * Aggregate는 결과가 DB 불변식을 위반하지 않는지 검증합니다.</p>
     *
     * @param xpDelta 반영할 XP 증감량
     * @param calculatedLevel 반영 후 계산된 레벨
     */
    public void applyXp(long xpDelta, int calculatedLevel) {
        long changedTotalXp;

        try {
            changedTotalXp = Math.addExact(totalXp, xpDelta);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "XP 범위를 초과했습니다."
            );
        }

        if (changedTotalXp < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "누적 XP는 0 이상이어야 합니다."
            );
        }

        if (calculatedLevel < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "레벨은 1 이상이어야 합니다."
            );
        }

        this.totalXp = changedTotalXp;
        this.level = calculatedLevel;
    }

    /**
     * Asia/Seoul 기준 유효 학습 날짜를 스트릭에 반영합니다.
     *
     * <p>같은 날짜의 중복 활동은 스트릭을 증가시키지 않고,
     * 바로 다음 날 활동은 스트릭을 이어가며, 하루 이상 건너뛴 활동은
     * 현재 스트릭을 1일부터 다시 시작합니다.</p>
     *
     * @param activityDate Asia/Seoul 기준 유효 학습 날짜
     */
    public void recordActivity(LocalDate activityDate) {
        LocalDate validatedDate = requireNonNull(
                activityDate,
                "학습 날짜는 필수입니다."
        );

        if (lastActivityDate == null) {
            startStreak(validatedDate);
            return;
        }

        if (validatedDate.isBefore(lastActivityDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "마지막 학습일보다 이전 날짜는 반영할 수 없습니다."
            );
        }

        if (validatedDate.isEqual(lastActivityDate)) {
            return;
        }

        if (validatedDate.isEqual(lastActivityDate.plusDays(1))) {
            currentStreak++;
        } else {
            currentStreak = 1;
        }

        longestStreak = Math.max(longestStreak, currentStreak);
        lastActivityDate = validatedDate;
    }

    /**
     * 최초 학습 활동으로 스트릭을 시작합니다.
     */
    private void startStreak(LocalDate activityDate) {
        currentStreak = 1;
        longestStreak = Math.max(longestStreak, currentStreak);
        lastActivityDate = activityDate;
    }

    /**
     * 필수 객체가 null인지 검증합니다.
     */
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
