package com.maesamco.content.application.command;

import com.maesamco.content.domain.entity.TagAttribute;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 태그 생성에 필요한 값입니다. Presentation의 요청 DTO와 분리된 Application 전용 타입입니다. */
@Getter
@AllArgsConstructor
public class TagCreateCommand {

    private final String name;
    private final TagAttribute attribute;
}
