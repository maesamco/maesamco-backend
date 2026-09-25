package com.maesamco.content.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
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

    /** 공개 상태. 새로 만든 콘텐츠는 DRAFT로 시작합니다(#344). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ContentStatus status;

    private Lesson(UUID unitId, String title, String description, String content, ProgrammingLanguage language, Integer displayOrder) {
        this.unitId = unitId;
        this.title = title;
        this.description = description;
        this.content = content;
        this.language = language;
        this.displayOrder = displayOrder;
        this.status = ContentStatus.DRAFT;
    }

    /** 유닛 생성 */
    public static Lesson create(UUID unitId, String title, String description, String content, ProgrammingLanguage language, Integer displayOrder) {
        return new Lesson(unitId, title, description, content, language, displayOrder);
    }

    /** 레슨 수정 */
    public void changeTitle(String title) { this.title = title; }
    public void changeDescription(String description) { this.description = description; }
    public void changeContent(String content) { this.content = content; }
    public void changeLanguage(ProgrammingLanguage language) { this.language = language; }
    public void changeDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    /** 학습자에게 공개합니다. 이미 공개 상태면 아무 변화가 없습니다. */
    public void publish() { this.status = ContentStatus.PUBLISHED; }

    /** 학습자 조회에서 내립니다. 이미 비공개 상태면 아무 변화가 없습니다. */
    public void unpublish() { this.status = ContentStatus.DRAFT; }

    /** 이 항목 자체가 공개 상태인지 확인합니다. 상위 항목의 공개 여부는 포함하지 않습니다. */
    public boolean isPublished() { return this.status == ContentStatus.PUBLISHED; }
}
