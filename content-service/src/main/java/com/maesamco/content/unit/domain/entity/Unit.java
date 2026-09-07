package com.maesamco.content.unit.domain.entity;

import com.maesamco.content.unit.domain.enums.ProgrammingLanguage;
import com.maesamco.content.global.common.BaseEntity;
import jakarta.persistence.*;
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

    private Unit(UUID curriculumId, String title, ProgrammingLanguage language, Integer displayOrder) {
        this.curriculumId = curriculumId;
        this.title = title;
        this.language = language;
        this.displayOrder = displayOrder;
    }

    /** 유닛 생성 */
    public static Unit create(UUID curriculumId, String title, ProgrammingLanguage language, Integer displayOrder) {
        return new Unit(curriculumId, title, language, displayOrder);
    }

    /** 유닛 제목 수정 */
    public void changeTitle(String newTitle) { this.title = newTitle; }

    /** 프로그래밍 언어 수정 */
    public void changeLanguage(ProgrammingLanguage newLanguage) { this.language = newLanguage; }

    /** 유닛 표시 순서 수정 */
    public void changeDisplayOrder(Integer newDisplayOrder) { this.displayOrder = newDisplayOrder; }
}