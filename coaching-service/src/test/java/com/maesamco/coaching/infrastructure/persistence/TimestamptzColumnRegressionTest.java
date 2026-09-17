package com.maesamco.coaching.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이슈 #218 — {@code BaseEntity}를 상속하지 않는 엔티티의 시각 필드는
 * {@code AiFeedbackRepositoryImplTest.schema_usesJsonbAndTimestamptzColumnTypes()}와
 * 같은 회귀 테스트가 없었다. 누군가 실수로 {@code Instant}를 {@code LocalDateTime}으로
 * 되돌려도 지금까지는 CI가 못 잡아냈다.
 *
 * <p>{@code AiFeedback.created_at}은 이미 위 테스트로 커버되고 있어 여기서 다시 넣지
 * 않는다. 각 엔티티의 실제 업무 로직과 무관한 순수 스키마 검증이라, 엔티티별
 * RepositoryImplTest 7개에 나눠 넣는 대신 파라미터라이즈 테스트 하나로 모았다
 * (이슈 #218 본문의 대안 중 후자를 채택).</p>
 */
class TimestamptzColumnRegressionTest extends AbstractCoachingRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    static Stream<Arguments> timestamptzColumns() {
        return Stream.of(
                Arguments.of("p_ai_call_histories", "called_at"),
                Arguments.of("p_hints", "created_at"),
                Arguments.of("p_explanations", "created_at"),
                Arguments.of("p_follow_up_questions", "created_at"),
                Arguments.of("p_follow_up_answers", "answered_at"),
                Arguments.of("p_coaching_sessions", "created_at"),
                Arguments.of("p_coaching_sessions", "completed_at"),
                Arguments.of("p_weak_concepts", "last_detected_at")
        );
    }

    @ParameterizedTest(name = "{0}.{1}은 timestamptz로 생성된다")
    @MethodSource("timestamptzColumns")
    @DisplayName("BaseEntity를 상속하지 않는 엔티티의 시각 컬럼도 timestamptz로 생성된다")
    void column_isTimestamptz(String tableName, String columnName) {
        assertThat(columnDataType(tableName, columnName))
                .isEqualTo("timestamp with time zone");
    }

    @SuppressWarnings("unchecked")
    private String columnDataType(String tableName, String columnName) {
        return (String) entityManager.createNativeQuery(
                        "SELECT data_type FROM information_schema.columns "
                                + "WHERE table_schema = 'coaching_schema' "
                                + "AND table_name = :tableName AND column_name = :columnName"
                )
                .setParameter("tableName", tableName)
                .setParameter("columnName", columnName)
                .getSingleResult();
    }
}
