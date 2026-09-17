package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataLessonRepository extends JpaRepository<Lesson, UUID> {

    /** 삭제되지 않은 레슨 단건 조회 */
    Optional<Lesson> findByIdAndDeletedAtIsNull(UUID lessonId);

    /** 특정 유닛에 속한 삭제되지 않은 레슨 수 조회 */
    long countByUnitIdAndDeletedAtIsNull(UUID unitId);

    /** 특정 유닛에 속한 삭제되지 않은 레슨 목록을 displayOrder, id 오름차순으로 페이지 조회 */
    Page<Lesson> findByUnitIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            UUID unitId,
            Pageable pageable
    );
}