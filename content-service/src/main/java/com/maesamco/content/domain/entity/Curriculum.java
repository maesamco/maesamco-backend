package com.maesamco.content.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "p_curriculums")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curriculum extends BaseEntity {

    /** 커리큘럼 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "curriculum_id",
            updatable = false,
            nullable = false
    )
    private UUID id;

    /** 커리큘럼 제목 */
    @Column(
            name = "title",
            length = 100,
            nullable = false
    )
    private String title;

    /** 프로그래밍 언어 */
    @Column(
            name = "language",
            length = 20,
            nullable = false
    )
    @jakarta.persistence.Enumerated(
            jakarta.persistence.EnumType.STRING
    )
    private ProgrammingLanguage language;

    private Curriculum(
            String title,
            ProgrammingLanguage language
    ) {
        this.title = title;
        this.language = language;
    }

    /** 커리큘럼 생성 */
    public static Curriculum create(
            String title,
            ProgrammingLanguage language
    ) {
        return new Curriculum(
                title,
                language
        );
    }

    /** 커리큘럼 제목 수정 */
    public void changeTitle(
            String newTitle
    ) {
        this.title = newTitle;
    }

    /** 커리큘럼 언어 수정 */
    public void changeLanguage(
            ProgrammingLanguage newLanguage
    ) {
        this.language = newLanguage;
    }
}
