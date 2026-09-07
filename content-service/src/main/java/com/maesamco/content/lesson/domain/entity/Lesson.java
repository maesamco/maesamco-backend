package com.maesamco.content.lesson.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.unit.domain.entity.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_lessons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 100)
    private String description;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 20)
    private ProgrammingLanguage language;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    private Lesson(UUID unitId, String title, String description, String content, ProgrammingLanguage language, Integer displayOrder) {
        this.unitId = unitId;
        this.title = title;
        this.description = description;
        this.content = content;
        this.language = language;
        this.displayOrder = displayOrder;
    }

    /** 유닛 생성 */
    public static Lesson create(UUID unitId, String title, String description, String content, ProgrammingLanguage language, Integer displayOrder) {
        return new Lesson(unitId, title, description, content, language, displayOrder);
    }

    /** 레슨 제목을 수정합니다. */
    public void updateTitle(String title) { this.title = title; }

    /** 레슨 설명을 수정합니다. */
    public void updateDescription(String description) { this.description = description; }

    /** 레슨 학습 내용을 수정합니다. */
    public void updateContent(String content) { this.content = content; }

    /** 레슨 언어를 수정합니다. */
    public void updateLanguage(ProgrammingLanguage language) { this.language = language; }

    /** 레슨 배치 순서를 수정합니다. */
    public void updateDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}