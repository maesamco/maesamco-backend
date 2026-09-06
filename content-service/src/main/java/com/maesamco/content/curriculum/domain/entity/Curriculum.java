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

    /** 프로그래밍 언어 */
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 20, nullable = false)
    private ProgrammingLanguage language;

    /** 커리큘럼 제목 */
    @Column(name = "title", length = 100, nullable = false)
    private String title;

    /** 커리큘럼 표시 순서 */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    private Curriculum(ProgrammingLanguage language, String title, Integer displayOrder) {
        this.language = language;
        this.title = title;
        this.displayOrder = displayOrder;
    }

    /** 커리큘럼 생성 */
    public static Curriculum create(ProgrammingLanguage language, String title, Integer displayOrder) {
        return new Curriculum(language, title, displayOrder);
    }

    /** 커리큘럼 수정 */
    public void update(ProgrammingLanguage language, String title, Integer displayOrder) {
        this.language = language;
        this.title = title;
        this.displayOrder = displayOrder;
    }
}