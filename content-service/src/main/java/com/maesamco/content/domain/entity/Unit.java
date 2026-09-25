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
@Table(name = "p_units")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Unit extends BaseEntity {

    /** 유닛 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "unit_id", updatable = false, nullable = false)
    private UUID id;

    /** 커리큘럼 식별자 */
    @Column(name = "curriculum_id", nullable = false)
    private UUID curriculumId;

    /** 유닛 제목 */
    @Column(name = "title", length = 100, nullable = false)
    private String title;

    /** 프로그래밍 언어 */
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 20, nullable = false)
    private ProgrammingLanguage language;

    /** 유닛 표시 순서 */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /** 공개 상태. 새로 만든 콘텐츠는 DRAFT로 시작합니다(#344). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ContentStatus status;

    private Unit(UUID curriculumId, String title, ProgrammingLanguage language, Integer displayOrder) {
        this.curriculumId = curriculumId;
        this.title = title;
        this.language = language;
        this.displayOrder = displayOrder;
        this.status = ContentStatus.DRAFT;
    }

    /** 유닛 생성 */
    public static Unit create(UUID curriculumId, String title, ProgrammingLanguage language, Integer displayOrder) {
        return new Unit(curriculumId, title, language, displayOrder);
    }

    /** 유닛 수정 */
    public void changeTitle(String newTitle) { this.title = newTitle; }
    public void changeLanguage(ProgrammingLanguage newLanguage) { this.language = newLanguage; }
    public void changeDisplayOrder(Integer newDisplayOrder) { this.displayOrder = newDisplayOrder; }

    /** 학습자에게 공개합니다. 이미 공개 상태면 아무 변화가 없습니다. */
    public void publish() { this.status = ContentStatus.PUBLISHED; }

    /** 학습자 조회에서 내립니다. 이미 비공개 상태면 아무 변화가 없습니다. */
    public void unpublish() { this.status = ContentStatus.DRAFT; }

    /** 이 항목 자체가 공개 상태인지 확인합니다. 상위 항목의 공개 여부는 포함하지 않습니다. */
    public boolean isPublished() { return this.status == ContentStatus.PUBLISHED; }
}
