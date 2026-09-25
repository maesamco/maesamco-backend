package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository {

    Lesson save(Lesson lesson);

    Optional<Lesson> findById(UUID lessonId);

    int findMaxDisplayOrderByUnitId(UUID unitId);

    Page<Lesson> searchLessons(UUID unitId, Pageable pageable);

    /**
     * 같은 Unit의 활성 Lesson을 displayOrder, id 오름차순으로 조회합니다(#324).
     * 순서 변경 시 부모 Unit을 잠근 뒤 호출합니다.
     */
    List<Lesson> findActiveSiblings(UUID unitId);

    /**
     * 주어진 목록 순서대로 displayOrder를 1..N으로 다시 부여합니다(#324).
     * 목록은 같은 Unit의 활성 Lesson 전체여야 합니다.
     */
    void reorder(List<Lesson> lessonsInOrder);
}