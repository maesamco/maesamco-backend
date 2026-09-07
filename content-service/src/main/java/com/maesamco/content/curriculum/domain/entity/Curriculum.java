package com.maesamco.content.curriculum.domain.entity;

import com.maesamco.content.curriculum.domain.enums.ProgrammingLanguage;
import com.maesamco.content.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_curriculums")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curriculum extends BaseEntity {

    /** 커리큘럼 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "curriculum_id", updatable = false, nullable = false)
    private UUID id;

    /** 커리큘럼 제목 */
    @Column(name = "title", length = 100, nullable = false)
    private String title;

    /** 프로그래밍 언어 */
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 20, nullable = false)
    private ProgrammingLanguage language;

    /** 커리큘럼 표시 순서 */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    private Curriculum(String title, ProgrammingLanguage language, Integer displayOrder) {
        this.title = title;
        this.language = language;
        this.displayOrder = displayOrder;
    }

    /** 커리큘럼 생성 */
    public static Curriculum create(String title, ProgrammingLanguage language, Integer displayOrder) {
        return new Curriculum(title, language, displayOrder);
    }

    /** 커리큘럼 수정 */
    public void changeTitle(String newTitle) { this.title = newTitle; }
    public void changeLanguage(ProgrammingLanguage newLanguage) { this.language = newLanguage; }
    public void changeDisplayOrder(Integer newDisplayOrder) { this.displayOrder = newDisplayOrder; }
}