package com.maesamco.content.presentation.response;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 태그 생성 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TagCreateResponse {

    private final UUID id;
    private final String name;
    private final TagAttribute attribute;

    public static TagCreateResponse from(Tag tag) {
        return new TagCreateResponse(
                tag.getId(),
                tag.getName(),
                tag.getAttribute()
        );
    }
}