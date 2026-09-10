package com.maesamco.content.lesson.presentation.dto.response;

import com.maesamco.content.lesson.domain.entity.Lesson;
import com.maesamco.content.lesson.domain.enums.ProgrammingLanguage;
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

    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getUnitId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getContent(),
                lesson.getLanguage(),
                lesson.getDisplayOrder()
        );
    }
}