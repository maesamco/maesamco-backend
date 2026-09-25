package com.maesamco.content.presentation.response;

import com.maesamco.content.application.result.TagResult;
import com.maesamco.content.domain.entity.TagAttribute;
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

    public static TagResponse from(TagResult tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getAttribute()
        );
    }
}