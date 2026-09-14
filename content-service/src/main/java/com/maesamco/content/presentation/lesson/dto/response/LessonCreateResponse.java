package com.maesamco.content.lesson.presentation.dto.response;

import com.maesamco.content.lesson.domain.entity.Lesson;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 레슨 생성 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LessonCreateResponse {

    private final UUID id;
    private final UUID unitId;
    private final String title;

    public static LessonCreateResponse from(Lesson lesson) {
        return new LessonCreateResponse(
                lesson.getId(),
                lesson.getUnitId(),
                lesson.getTitle()
        );
    }
}