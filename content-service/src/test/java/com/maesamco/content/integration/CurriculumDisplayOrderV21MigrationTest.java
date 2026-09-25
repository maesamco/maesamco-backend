package com.maesamco.content.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V21 Curriculum/Unit/Lesson display_order 정리 마이그레이션 회귀 테스트(#324).
 *
 * <p>#318의 V20까지 적용한 DB에 중복·공백·삭제 행이 섞인 순서를 넣고 V21을 적용해 검증합니다.</p>
 */
@Testcontainers
@DisplayName("V21 Curriculum/Unit/Lesson display_order Migration 통합 테스트 (#324)")
class CurriculumDisplayOrderV21MigrationTest {

    private static final UUID SYSTEM = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final UUID CURRICULUM_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID CURRICULUM_2 = UUID.fromString("c0000000-0000-0000-0000-000000000002");

    // UUID 오름차순: UNIT_A < UNIT_B < UNIT_C < UNIT_DELETED
    private static final UUID UNIT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID UNIT_C = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID UNIT_DELETED = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID UNIT_OTHER_PARENT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    // UUID 오름차순: LESSON_A < LESSON_B < LESSON_DELETED
    private static final UUID LESSON_A = UUID.fromString("10000000-0000-0000-0000-0000000000a1");
    private static final UUID LESSON_B = UUID.fromString("10000000-0000-0000-0000-0000000000b1");
    private static final UUID LESSON_DELETED = UUID.fromString("10000000-0000-0000-0000-0000000000d1");
    private static final UUID LESSON_OTHER_PARENT = UUID.fromString("10000000-0000-0000-0000-0000000000e1");

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Test
    @DisplayName("V21은 활성 형제의 중복·공백 순서를 부모별로 1..N 재정렬하고(동률은 ID 순), 삭제 행은 그대로 두며, 활성 중복 INSERT를 막는다")
    void migrateV21_normalizesActiveSiblingOrders() throws SQLException {
        // given — #318의 V20까지 적용
        flyway("20").migrate();

        assertThat(appliedVersions()).last().isEqualTo("20");
        assertThat(columnExists("p_curriculums", "display_order")).isTrue();

        insertCurriculum(CURRICULUM_1);
        insertCurriculum(CURRICULUM_2);

        // 커리큘럼 1: 활성 [B=2, A=2(동률), C=7(공백)], 삭제 [DELETED=2]
        insertUnit(UNIT_B, CURRICULUM_1, 2, false);
        insertUnit(UNIT_A, CURRICULUM_1, 2, false);
        insertUnit(UNIT_C, CURRICULUM_1, 7, false);
        insertUnit(UNIT_DELETED, CURRICULUM_1, 2, true);
        // 커리큘럼 2: 활성 [OTHER=5] — 다른 부모와 섞이지 않아야 한다
        insertUnit(UNIT_OTHER_PARENT, CURRICULUM_2, 5, false);

        // 유닛 A: 활성 [B=3, A=3(동률)], 삭제 [DELETED=1]
        insertLesson(LESSON_B, UNIT_A, 3, false);
        insertLesson(LESSON_A, UNIT_A, 3, false);
        insertLesson(LESSON_DELETED, UNIT_A, 1, true);
        // 유닛 B: 활성 [OTHER=9]
        insertLesson(LESSON_OTHER_PARENT, UNIT_B, 9, false);

        // when — #317의 V21 적용
        assertThatCode(() -> flyway("21").migrate()).doesNotThrowAnyException();

        // then — V20 다음에 V21이 적용됐다
        assertThat(appliedVersions()).containsSubsequence("20", "21");

        // Curriculum은 순서 개념을 쓰지 않는다
        assertThat(columnExists("p_curriculums", "display_order")).isFalse();

        // 활성 Unit: 동률은 ID 오름차순(A → B), 공백은 채워진다(C: 7 → 3)
        assertThat(displayOrder("p_units", "unit_id", UNIT_A)).isEqualTo(1);
        assertThat(displayOrder("p_units", "unit_id", UNIT_B)).isEqualTo(2);
        assertThat(displayOrder("p_units", "unit_id", UNIT_C)).isEqualTo(3);

        // 삭제 행은 바뀌지 않는다
        assertThat(displayOrder("p_units", "unit_id", UNIT_DELETED)).isEqualTo(2);

        // 부모별로 따로 1부터 매긴다
        assertThat(displayOrder("p_units", "unit_id", UNIT_OTHER_PARENT)).isEqualTo(1);

        // 활성 Lesson도 같은 규칙
        assertThat(displayOrder("p_lessons", "lesson_id", LESSON_A)).isEqualTo(1);
        assertThat(displayOrder("p_lessons", "lesson_id", LESSON_B)).isEqualTo(2);
        assertThat(displayOrder("p_lessons", "lesson_id", LESSON_DELETED)).isEqualTo(1);
        assertThat(displayOrder("p_lessons", "lesson_id", LESSON_OTHER_PARENT)).isEqualTo(1);

        // 활성 형제와 같은 순서의 INSERT는 부분 UNIQUE 인덱스로 거절된다
        assertThatThrownBy(() -> insertUnit(UUID.randomUUID(), CURRICULUM_1, 1, false))
                .hasMessageContaining("uq_p_units_active_curriculum_display_order");

        assertThatThrownBy(() -> insertLesson(UUID.randomUUID(), UNIT_A, 1, false))
                .hasMessageContaining("uq_p_lessons_active_unit_display_order");

        // 삭제 행은 인덱스 대상이 아니므로 같은 순서로 저장할 수 있다
        assertThatCode(() -> insertUnit(UUID.randomUUID(), CURRICULUM_1, 1, true))
                .doesNotThrowAnyException();

        assertThatCode(() -> insertLesson(UUID.randomUUID(), UNIT_A, 1, true))
                .doesNotThrowAnyException();

        // 다른 부모에서는 같은 순서를 쓸 수 있다
        assertThatCode(() -> insertUnit(UUID.randomUUID(), CURRICULUM_2, 2, false))
                .doesNotThrowAnyException();
    }

    private Flyway flyway(String version) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("content_schema")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private void insertCurriculum(UUID curriculumId) throws SQLException {
        execute(
                """
                INSERT INTO content_schema.p_curriculums
                (curriculum_id, title, display_order, language,
                 created_at, created_by, updated_at, updated_by)
                VALUES (?, '커리큘럼', 1, 'JAVA', NOW(), ?, NOW(), ?)
                """,
                curriculumId, SYSTEM, SYSTEM
        );
    }

    private void insertUnit(UUID unitId, UUID curriculumId, int displayOrder, boolean deleted) throws SQLException {
        execute(
                """
                INSERT INTO content_schema.p_units
                (unit_id, curriculum_id, title, display_order, language,
                 created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
                VALUES (?, ?, '유닛', ?, 'JAVA', NOW(), ?, NOW(), ?,
                        CASE WHEN ? THEN NOW() END, CASE WHEN ? THEN ?::uuid END)
                """,
                unitId, curriculumId, displayOrder, SYSTEM, SYSTEM, deleted, deleted, SYSTEM
        );
    }

    private void insertLesson(UUID lessonId, UUID unitId, int displayOrder, boolean deleted) throws SQLException {
        execute(
                """
                INSERT INTO content_schema.p_lessons
                (lesson_id, unit_id, title, description, content, display_order, language,
                 created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
                VALUES (?, ?, '레슨', '설명', '내용', ?, 'JAVA', NOW(), ?, NOW(), ?,
                        CASE WHEN ? THEN NOW() END, CASE WHEN ? THEN ?::uuid END)
                """,
                lessonId, unitId, displayOrder, SYSTEM, SYSTEM, deleted, deleted, SYSTEM
        );
    }

    private Integer displayOrder(String table, String idColumn, UUID id) throws SQLException {
        try (
                Connection connection = postgres.createConnection("");
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT display_order FROM content_schema." + table + " WHERE " + idColumn + " = ?"
                )
        ) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (
                Connection connection = postgres.createConnection("");
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'content_schema' AND table_name = ? AND column_name = ?
                        """
                )
        ) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    /** installed_rank 순서대로 적용된 버전 목록 */
    private List<String> appliedVersions() throws SQLException {
        try (
                Connection connection = postgres.createConnection("");
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT version FROM content_schema.flyway_schema_history
                        WHERE success AND version IS NOT NULL
                        ORDER BY installed_rank
                        """
                );
                ResultSet resultSet = statement.executeQuery()
        ) {
            List<String> versions = new ArrayList<>();
            while (resultSet.next()) {
                versions.add(resultSet.getString(1));
            }
            return versions;
        }
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (
                Connection connection = postgres.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
}
