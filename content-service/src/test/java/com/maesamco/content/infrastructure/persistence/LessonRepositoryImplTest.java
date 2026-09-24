package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.LessonRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
        FlywayAutoConfiguration.class
)
@Import({
        JpaAuditingConfig.class,
        LessonRepositoryImpl.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                SpringDataLessonRepository.class
        }
)
class LessonRepositoryImplTest {

    private static final UUID TEST_USER_ID =
            UUID.randomUUID();

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    static {
        postgres.start();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LessonRepository lessonRepository;

    private UUID unitId;

    private UUID otherUnitId;

    @BeforeEach
    void setUp() {
        Curriculum curriculum =
                Curriculum.create("Java 기본 과정", ProgrammingLanguage.JAVA);

        entityManager.persist(
                curriculum
        );

        entityManager.flush();

        Unit unit =
                Unit.create(
                        curriculum.getId(),
                        "Java 기초",
                        ProgrammingLanguage.JAVA,
                        1
                );

        Unit otherUnit =
                Unit.create(
                        curriculum.getId(),
                        "Java 중급",
                        ProgrammingLanguage.JAVA,
                        2
                );

        entityManager.persist(
                unit
        );

        entityManager.persist(
                otherUnit
        );

        entityManager.flush();

        unitId =
                unit.getId();

        otherUnitId =
                otherUnit.getId();
    }

    @Test
    @DisplayName(
            "Lesson을 저장하면 실제 PostgreSQL에 저장된다"
    )
    void save_persistsLesson() {
        // given
        Lesson lesson =
                createLesson(
                        unitId,
                        "변수와 자료형",
                        1
                );

        // when
        Lesson saved =
                lessonRepository.save(
                        lesson
                );

        entityManager.flush();

        UUID lessonId =
                saved.getId();

        entityManager.clear();

        // then
        Lesson found =
                lessonRepository.findById(
                                lessonId
                        )
                        .orElseThrow();

        assertThat(found.getId())
                .isEqualTo(
                        lessonId
                );

        assertThat(found.getUnitId())
                .isEqualTo(
                        unitId
                );

        assertThat(found.getTitle())
                .isEqualTo(
                        "변수와 자료형"
                );

        assertThat(found.getLanguage())
                .isEqualTo(
                        ProgrammingLanguage.JAVA
                );

        assertThat(found.getDescription())
                .isEqualTo(
                        "변수와 자료형 설명"
                );

        assertThat(found.getContent())
                .isEqualTo(
                        "변수와 자료형 내용"
                );

        assertThat(found.getDisplayOrder())
                .isEqualTo(
                        1
                );

        assertThat(found.getDeletedAt())
                .isNull();

        assertThat(found.getDeletedBy())
                .isNull();
    }

    @Test
    @DisplayName(
            "삭제되지 않은 Lesson은 ID로 조회할 수 있다"
    )
    void findById_activeLesson_returnsLesson() {
        // given
        Lesson lesson =
                createLesson(
                        unitId,
                        "조건문",
                        1
                );

        Lesson saved =
                lessonRepository.save(
                        lesson
                );

        entityManager.flush();

        UUID lessonId =
                saved.getId();

        entityManager.clear();

        // when
        Optional<Lesson> result =
                lessonRepository.findById(
                        lessonId
                );

        // then
        assertThat(result)
                .isPresent();

        Lesson found =
                result.orElseThrow();

        assertThat(found.getId())
                .isEqualTo(
                        lessonId
                );

        assertThat(found.getUnitId())
                .isEqualTo(
                        unitId
                );

        assertThat(found.getTitle())
                .isEqualTo(
                        "조건문"
                );
    }

    @Test
    @DisplayName(
            "soft delete된 Lesson은 ID 조회 결과에서 제외된다"
    )
    void findById_softDeletedLesson_returnsEmpty() {
        // given
        Lesson lesson =
                createLesson(
                        unitId,
                        "반복문",
                        1
                );

        lessonRepository.save(
                lesson
        );

        entityManager.flush();

        UUID lessonId =
                lesson.getId();

        lesson.softDelete(
                TEST_USER_ID
        );

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Lesson> result =
                lessonRepository.findById(
                        lessonId
                );

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName(
            "존재하지 않는 Lesson ID를 조회하면 빈 Optional을 반환한다"
    )
    void findById_unknownId_returnsEmpty() {
        // given
        UUID unknownLessonId =
                UUID.randomUUID();

        // when
        Optional<Lesson> result =
                lessonRepository.findById(
                        unknownLessonId
                );

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName(
            "Unit에 속한 삭제되지 않은 Lesson의 개수만 반환한다"
    )
    void countByUnitId_countsOnlyActiveLessonsOfUnit() {
        // given
        Lesson first =
                createLesson(
                        unitId,
                        "변수",
                        1
                );

        Lesson second =
                createLesson(
                        unitId,
                        "조건문",
                        2
                );

        Lesson deleted =
                createLesson(
                        unitId,
                        "삭제된 레슨",
                        3
                );

        Lesson otherUnitLesson =
                createLesson(
                        otherUnitId,
                        "다른 Unit 레슨",
                        1
                );

        lessonRepository.save(
                first
        );

        lessonRepository.save(
                second
        );

        lessonRepository.save(
                deleted
        );

        lessonRepository.save(
                otherUnitLesson
        );

        entityManager.flush();

        deleted.softDelete(
                TEST_USER_ID
        );

        entityManager.flush();
        entityManager.clear();

        // when
        int count =
                lessonRepository.findMaxDisplayOrderByUnitId(
                        unitId
                );

        // then
        assertThat(count)
                .isEqualTo(
                        2L
                );
    }

    @Test
    @DisplayName(
            "Lesson 개수 조회 시 다른 Unit의 Lesson은 포함하지 않는다"
    )
    void countByUnitId_excludesLessonsOfOtherUnit() {
        // given
        lessonRepository.save(
                createLesson(
                        unitId,
                        "현재 Unit 레슨",
                        1
                )
        );

        lessonRepository.save(
                createLesson(
                        otherUnitId,
                        "다른 Unit 레슨 1",
                        1
                )
        );

        lessonRepository.save(
                createLesson(
                        otherUnitId,
                        "다른 Unit 레슨 2",
                        2
                )
        );

        entityManager.flush();
        entityManager.clear();

        // when
        int count =
                lessonRepository.findMaxDisplayOrderByUnitId(
                        unitId
                );

        // then
        assertThat(count)
                .isEqualTo(
                        1L
                );
    }

    @Test
    @DisplayName(
            "Lesson 목록은 같은 Unit의 삭제되지 않은 Lesson만 displayOrder 오름차순으로 조회한다"
    )
    void searchLessons_returnsActiveLessonsInDisplayOrder() {
        // given
        Lesson third =
                createLesson(
                        unitId,
                        "세 번째",
                        3
                );

        Lesson first =
                createLesson(
                        unitId,
                        "첫 번째",
                        1
                );

        Lesson second =
                createLesson(
                        unitId,
                        "두 번째",
                        2
                );

        Lesson deleted =
                createLesson(
                        unitId,
                        "삭제된 레슨",
                        0
                );

        Lesson otherUnitLesson =
                createLesson(
                        otherUnitId,
                        "다른 Unit 레슨",
                        1
                );

        lessonRepository.save(
                third
        );

        lessonRepository.save(
                first
        );

        lessonRepository.save(
                second
        );

        lessonRepository.save(
                deleted
        );

        lessonRepository.save(
                otherUnitLesson
        );

        entityManager.flush();

        deleted.softDelete(
                TEST_USER_ID
        );

        entityManager.flush();
        entityManager.clear();

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Lesson> result =
                lessonRepository.searchLessons(
                        unitId,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(
                        3
                );

        assertThat(result.getContent())
                .extracting(
                        Lesson::getTitle
                )
                .containsExactly(
                        "첫 번째",
                        "두 번째",
                        "세 번째"
                );

        assertThat(result.getContent())
                .extracting(
                        Lesson::getDisplayOrder
                )
                .containsExactly(
                        1,
                        2,
                        3
                );

        assertThat(result.getContent())
                .extracting(
                        Lesson::getUnitId
                )
                .containsOnly(
                        unitId
                );

        assertThat(result.getContent())
                .allMatch(
                        lesson ->
                                lesson.getDeletedAt()
                                        == null
                );

        assertThat(result.getTotalElements())
                .isEqualTo(
                        3
                );
    }

    @Test
    @DisplayName(
            "Lesson 목록 조회에 Pageable의 page와 size를 적용한다"
    )
    void searchLessons_appliesPagination() {
        // given
        lessonRepository.save(
                createLesson(
                        unitId,
                        "첫 번째",
                        1
                )
        );

        lessonRepository.save(
                createLesson(
                        unitId,
                        "두 번째",
                        2
                )
        );

        lessonRepository.save(
                createLesson(
                        unitId,
                        "세 번째",
                        3
                )
        );

        lessonRepository.save(
                createLesson(
                        unitId,
                        "네 번째",
                        4
                )
        );

        entityManager.flush();
        entityManager.clear();

        Pageable pageable =
                PageRequest.of(
                        1,
                        2
                );

        // when
        Page<Lesson> result =
                lessonRepository.searchLessons(
                        unitId,
                        pageable
                );

        // then
        assertThat(result.getNumber())
                .isEqualTo(
                        1
                );

        assertThat(result.getSize())
                .isEqualTo(
                        2
                );

        assertThat(result.getContent())
                .extracting(
                        Lesson::getTitle
                )
                .containsExactly(
                        "세 번째",
                        "네 번째"
                );

        assertThat(result.getTotalElements())
                .isEqualTo(
                        4
                );

        assertThat(result.getTotalPages())
                .isEqualTo(
                        2
                );

        assertThat(result.hasPrevious())
                .isTrue();

        assertThat(result.hasNext())
                .isFalse();
    }

    @Test
    @DisplayName(
            "해당 Unit에 Lesson이 없으면 빈 페이지를 반환한다"
    )
    void searchLessons_noLessons_returnsEmptyPage() {
        // given
        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Lesson> result =
                lessonRepository.searchLessons(
                        unitId,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .isEmpty();

        assertThat(result.getTotalElements())
                .isZero();

        assertThat(result.getTotalPages())
                .isZero();
    }

        private Lesson createLesson(
            UUID unitId,
            String title,
            int displayOrder
    ) {
        return Lesson.create(
                unitId,
                title,
                title + " 설명",
                title + " 내용",
                ProgrammingLanguage.JAVA,
                displayOrder
        );
    }
}