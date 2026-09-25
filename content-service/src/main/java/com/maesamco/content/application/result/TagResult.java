package com.maesamco.content.application.result;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 태그 조회·생성 결과입니다. Presentation의 응답 DTO와 분리된 Application 전용 타입입니다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TagResult {

    private final UUID id;
    private final String name;
    private final TagAttribute attribute;

    public static TagResult from(Tag tag) {
        return new TagResult(
                tag.getId(),
                tag.getName(),
                tag.getAttribute()
        );
    }
}
