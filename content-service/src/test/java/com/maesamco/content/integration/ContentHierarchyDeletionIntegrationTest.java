package com.maesamco.content.integration;

import com.maesamco.content.application.finder_service.CurriculumFinderService;
import com.maesamco.content.application.finder_service.LessonFinderService;
import com.maesamco.content.application.finder_service.UnitFinderService;
import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.domain.repository.LessonRepository;
import com.maesamco.content.domain.repository.UnitRepository;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.persistence.CurriculumRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.LessonRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.UnitRepositoryImpl;
import com.maesamco.content.presentation.request.LessonCreateRequest;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.request.UnitCreateRequest;
import com.maesamco.content.presentation.request.UnitUpdateRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        CurriculumRepositoryImpl.class,
        UnitRepositoryImpl.class,
        LessonRepositoryImpl.class,
        CurriculumFinderService.class,
        UnitFinderService.class,
        LessonFinderService.class
})
@EnableJpaRepositories(basePackages = "com.maesamco.content.infrastructure.persistence")
class ContentHierarchyDeletionIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }

    @Autowired private EntityManager entityManager;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private UnitRepository unitRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private CurriculumFinderService curriculumFinder;
    @Autowired private UnitFinderService unitFinder;
    @Autowired private LessonFinderService lessonFinder;

    @Test
    @DisplayName("Curriculum 삭제 시 하위 데이터는 보존하고 Unit/Lesson 서비스 접근은 차단한다")
    void deletingCurriculumKeepsChildrenButBlocksTheirServiceEndpoints() {
        Hierarchy ids = createHierarchy();
        CurriculumService curriculums = new CurriculumService(curriculumRepository, curriculumFinder);
        UnitService units = new UnitService(unitRepository, unitFinder, curriculumFinder);
        LessonService lessons = lessonService();

        curriculums.deleteCurriculum(ids.curriculumId(), UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        // 부모가 삭제되면 하위 리소스는 보존하되, 삭제된 부모를 거치는 Unit/Lesson 서비스 진입점은 막는다 (#344 제안). Problem-Lesson 연결 경로는 #348에서 다룬다.
        assertThat(activeRowCount("p_units", "unit_id", ids.unitId())).isEqualTo(1);
        assertThat(activeRowCount("p_lessons", "lesson_id", ids.lessonId())).isEqualTo(1);
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> units.getUnit(ids.unitId()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND,
                () -> units.searchUnits(ids.curriculumId(), PageRequest.of(0, 10)));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND,
                () -> units.updateUnit(ids.unitId(), new UnitUpdateRequest()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND,
                () -> units.deleteUnit(ids.unitId(), UUID.randomUUID()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> lessons.getLesson(ids.lessonId()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND,
                () -> lessons.searchLessons(ids.unitId(), PageRequest.of(0, 10)));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND,
                () -> lessons.updateLesson(ids.lessonId(), new LessonUpdateRequest()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND,
                () -> lessons.deleteLesson(ids.lessonId(), UUID.randomUUID()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND,
                () -> lessons.getLessonConcepts(ids.lessonId()));
    }

    @Test
    @DisplayName("Unit 삭제 시 Lesson 데이터는 보존하고 Lesson 서비스 접근은 차단한다")
    void deletingUnitKeepsLessonButBlocksItsEndpoints() {
        Hierarchy ids = createHierarchy();
        UnitService units = new UnitService(unitRepository, unitFinder, curriculumFinder);
        LessonService lessons = lessonService();

        units.deleteUnit(ids.unitId(), UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        assertThat(activeRowCount("p_lessons", "lesson_id", ids.lessonId())).isEqualTo(1);
        assertError(ErrorCode.UNIT_NOT_FOUND, () -> lessons.getLesson(ids.lessonId()));
        assertError(ErrorCode.UNIT_NOT_FOUND,
                () -> lessons.searchLessons(ids.unitId(), PageRequest.of(0, 10)));
        assertError(ErrorCode.UNIT_NOT_FOUND,
                () -> lessons.updateLesson(ids.lessonId(), new LessonUpdateRequest()));
        assertError(ErrorCode.UNIT_NOT_FOUND,
                () -> lessons.deleteLesson(ids.lessonId(), UUID.randomUUID()));
        assertError(ErrorCode.UNIT_NOT_FOUND,
                () -> lessons.getLessonConcepts(ids.lessonId()));
    }


    @Test
    @DisplayName("Curriculum 삭제 후 Unit 생성은 CURRICULUM_NOT_FOUND로 차단된다")
    void deletingCurriculumBlocksCreatingUnit() {
        // Given
        Hierarchy ids = createHierarchy();
        CurriculumService curriculums =
                new CurriculumService(curriculumRepository, curriculumFinder);
        UnitService units =
                new UnitService(unitRepository, unitFinder, curriculumFinder);

        curriculums.deleteCurriculum(ids.curriculumId(), UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        UnitCreateRequest request = mock(UnitCreateRequest.class);
        when(request.getCurriculumId()).thenReturn(ids.curriculumId());

        // When & Then
        assertError(
                ErrorCode.CURRICULUM_NOT_FOUND,
                () -> units.createUnit(request)
        );
    }

    @Test
    @DisplayName("Curriculum 삭제 후 기존 Unit 아래 Lesson 생성은 CURRICULUM_NOT_FOUND로 차단된다")
    void deletingCurriculumBlocksCreatingLessonUnderExistingUnit() {
        // Given
        Hierarchy ids = createHierarchy();
        CurriculumService curriculums =
                new CurriculumService(curriculumRepository, curriculumFinder);
        LessonService lessons = lessonService();

        curriculums.deleteCurriculum(ids.curriculumId(), UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        LessonCreateRequest request = mock(LessonCreateRequest.class);
        when(request.getUnitId()).thenReturn(ids.unitId());

        // When & Then
        assertError(
                ErrorCode.CURRICULUM_NOT_FOUND,
                () -> lessons.createLesson(request)
        );
    }

    @Test
    @DisplayName("Unit 삭제 후 Lesson 생성은 UNIT_NOT_FOUND로 차단된다")
    void deletingUnitBlocksCreatingLesson() {
        // Given
        Hierarchy ids = createHierarchy();
        UnitService units =
                new UnitService(unitRepository, unitFinder, curriculumFinder);
        LessonService lessons = lessonService();

        units.deleteUnit(ids.unitId(), UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        LessonCreateRequest request = mock(LessonCreateRequest.class);
        when(request.getUnitId()).thenReturn(ids.unitId());

        // When & Then
        assertError(
                ErrorCode.UNIT_NOT_FOUND,
                () -> lessons.createLesson(request)
        );
    }
    private Hierarchy createHierarchy() {
        Curriculum curriculum = curriculumRepository.save(Curriculum.create("Java", ProgrammingLanguage.JAVA));
        Unit unit = unitRepository.save(Unit.create(curriculum.getId(), "기초", ProgrammingLanguage.JAVA, 1));
        Lesson lesson = lessonRepository.save(
                Lesson.create(unit.getId(), "변수", "설명", "본문", ProgrammingLanguage.JAVA, 1));
        entityManager.flush();
        entityManager.clear();
        return new Hierarchy(curriculum.getId(), unit.getId(), lesson.getId());
    }

    private LessonService lessonService() {
        return new LessonService(lessonRepository, lessonFinder, unitFinder,
                mock(ProblemQueryRepository.class), mock(ProblemTagRepository.class));
    }

    private long activeRowCount(String table, String idColumn, UUID id) {
        // 테이블명은 테스트의 고정 문자열만 사용합니다.
        return ((Number) entityManager.createNativeQuery(
                "select count(*) from content_schema." + table
                        + " where " + idColumn + " = :id and deleted_at is null")
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private record Hierarchy(UUID curriculumId, UUID unitId, UUID lessonId) {}
}
