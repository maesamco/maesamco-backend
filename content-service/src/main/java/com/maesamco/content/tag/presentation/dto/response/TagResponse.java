package com.maesamco.content.tag.presentation.dto.response;

import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 태그 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TagResponse {

    private final UUID id;
    private final String name;
    private final TagAttribute attribute;

    public static TagResponse from(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getAttribute()
        );
    }
}