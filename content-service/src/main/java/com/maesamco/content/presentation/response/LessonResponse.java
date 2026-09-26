package com.maesamco.content.presentation.response;

import com.maesamco.content.domain.entity.ContentStatus;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 레슨 단건 조회 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LessonResponse {

    private final UUID id;
    private final UUID unitId;
    private final String title;
    private final String description;
    private final String content;
    private final ProgrammingLanguage language;
    private final Integer displayOrder;

    /** 공개 상태 (#359). 학습자 조회에서는 항상 PUBLISHED입니다. */
    private final ContentStatus status;

    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getUnitId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getContent(),
                lesson.getLanguage(),
                lesson.getDisplayOrder(),
                lesson.getStatus()
        );
    }
}