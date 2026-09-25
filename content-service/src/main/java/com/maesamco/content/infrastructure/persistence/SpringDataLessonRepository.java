package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataLessonRepository extends JpaRepository<Lesson, UUID> {

    /** 삭제되지 않은 레슨 단건 조회 */
    Optional<Lesson> findByIdAndDeletedAtIsNull(UUID lessonId);

    /** 특정 Unit의 활성 Lesson 중 가장 큰 displayOrder를 조회 */
    @Query("""
            select coalesce(max(lesson.displayOrder), 0)
            from Lesson lesson
            where lesson.unitId = :unitId
              and lesson.deletedAt is null
            """)
    int findMaxDisplayOrderByUnitId(
            @Param("unitId") UUID unitId
    );

    /** 특정 유닛에 속한 삭제되지 않은 레슨 목록을 displayOrder, id 오름차순으로 페이지 조회 */
    Page<Lesson> findByUnitIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            UUID unitId,
            Pageable pageable
    );

    /** 특정 유닛에 속한 삭제되지 않은 레슨 전체를 displayOrder, id 오름차순으로 조회 (#324) */
    List<Lesson> findByUnitIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            UUID unitId
    );
}