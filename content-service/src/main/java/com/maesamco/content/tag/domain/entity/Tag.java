package com.maesamco.content.tag.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute", nullable = false, length = 20)
    private TagAttribute attribute;

    private Tag(String name, TagAttribute attribute) {
        this.name = name;
        this.attribute = attribute;
    }

    /** 태그 생성 */
    public static Tag create(String name, TagAttribute attribute) {
        return new Tag(name, attribute);
    }

    /** 태그 수정 */
    public void changeName(String name) { this.name = name; }
    public void changeAttribute(TagAttribute attribute) { this.attribute = attribute; }
}