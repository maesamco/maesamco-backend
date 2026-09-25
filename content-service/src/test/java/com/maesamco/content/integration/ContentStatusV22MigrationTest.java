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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V22 Curriculum/Unit/Lesson 공개 상태 도입 마이그레이션 테스트(#344).
 *
 * <p>V21까지 적용한 DB에 활성·삭제 행을 넣고 V22를 적용해,
 * 기존 행은 PUBLISHED로 채워지고 이후 신규 행은 DRAFT가 기본값인지 검증합니다.</p>
 */
@Testcontainers
@DisplayName("V22 Curriculum/Unit/Lesson status Migration 통합 테스트 (#344)")
class ContentStatusV22MigrationTest {

    private static final UUID SYSTEM = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final UUID CURRICULUM = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT_DELETED = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID LESSON = UUID.fromString("10000000-0000-0000-0000-0000000000a1");

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Test
    @DisplayName("V22는 기존 행(삭제 행 포함)을 PUBLISHED로 채우고, 이후 신규 행의 기본값은 DRAFT이며, 허용되지 않은 값은 거절한다")
    void migrateV22_backfillsExistingRowsAsPublishedAndDefaultsNewRowsToDraft() throws SQLException {
        // given — V21까지 적용하고 기존 데이터를 넣는다
        flyway("21").migrate();

        insertCurriculum(CURRICULUM);
        insertUnit(UNIT, CURRICULUM, 1, false);
        insertUnit(UNIT_DELETED, CURRICULUM, 2, true);
        insertLesson(LESSON, UNIT, 1);

        // when — V22 적용
        assertThatCode(() -> flyway("22").migrate()).doesNotThrowAnyException();

        // then — 기존 행은 학습자에게 이미 보이던 콘텐츠이므로 PUBLISHED
        assertThat(status("p_curriculums", "curriculum_id", CURRICULUM)).isEqualTo("PUBLISHED");
        assertThat(status("p_units", "unit_id", UNIT)).isEqualTo("PUBLISHED");
        assertThat(status("p_units", "unit_id", UNIT_DELETED)).isEqualTo("PUBLISHED");
        assertThat(status("p_lessons", "lesson_id", LESSON)).isEqualTo("PUBLISHED");

        // then — V22 이후 status 없이 INSERT한 행은 DRAFT
        UUID newCurriculum = UUID.randomUUID();
        UUID newUnit = UUID.randomUUID();
        UUID newLesson = UUID.randomUUID();
        insertCurriculum(newCurriculum);
        insertUnit(newUnit, newCurriculum, 1, false);
        insertLesson(newLesson, newUnit, 1);

        assertThat(status("p_curriculums", "curriculum_id", newCurriculum)).isEqualTo("DRAFT");
        assertThat(status("p_units", "unit_id", newUnit)).isEqualTo("DRAFT");
        assertThat(status("p_lessons", "lesson_id", newLesson)).isEqualTo("DRAFT");

        // then — 허용 값 외에는 CHECK 제약으로 거절
        assertThatThrownBy(() -> updateStatus("p_curriculums", "curriculum_id", CURRICULUM, "ARCHIVED"))
                .hasMessageContaining("ck_p_curriculums_status");
        assertThatThrownBy(() -> updateStatus("p_units", "unit_id", UNIT, "ARCHIVED"))
                .hasMessageContaining("ck_p_units_status");
        assertThatThrownBy(() -> updateStatus("p_lessons", "lesson_id", LESSON, "ARCHIVED"))
                .hasMessageContaining("ck_p_lessons_status");
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
                (curriculum_id, title, language, created_at, created_by, updated_at, updated_by)
                VALUES (?, '커리큘럼', 'JAVA', NOW(), ?, NOW(), ?)
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

    private void insertLesson(UUID lessonId, UUID unitId, int displayOrder) throws SQLException {
        execute(
                """
                INSERT INTO content_schema.p_lessons
                (lesson_id, unit_id, title, description, content, display_order, language,
                 created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, '레슨', '설명', '내용', ?, 'JAVA', NOW(), ?, NOW(), ?)
                """,
                lessonId, unitId, displayOrder, SYSTEM, SYSTEM
        );
    }

    private String status(String table, String idColumn, UUID id) throws SQLException {
        try (
                Connection connection = postgres.createConnection("");
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT status FROM content_schema." + table + " WHERE " + idColumn + " = ?"
                )
        ) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private void updateStatus(String table, String idColumn, UUID id, String status) throws SQLException {
        execute("UPDATE content_schema." + table + " SET status = ? WHERE " + idColumn + " = ?", status, id);
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
