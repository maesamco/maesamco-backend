package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.Lesson;

import java.util.UUID;

/** 레슨 조회 기능을 제공합니다. */
public interface LessonFinder {

    /** 레슨 단건 조회 */
    Lesson getById(UUID lessonId);

    /**
     * 학습자 조회용으로 레슨을 조회합니다(#359).
     * 레슨·유닛·커리큘럼 중 삭제되거나 공개(PUBLISHED)되지 않은 항목이 있으면
     * 해당 계층의 NOT_FOUND(LESSON / UNIT / CURRICULUM)를 던집니다.
     */
    Lesson getPublishedById(UUID lessonId);
}
