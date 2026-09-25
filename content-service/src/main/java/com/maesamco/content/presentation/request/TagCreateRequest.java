package com.maesamco.content.presentation.request;

import com.maesamco.content.application.command.TagCreateCommand;
import com.maesamco.content.domain.entity.TagAttribute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 태그 생성 요청 DTO */
@Getter
@NoArgsConstructor
public class TagCreateRequest {

    @NotBlank
    @Size(max = 50)
    private String name;

    @NotNull
    private TagAttribute attribute;

    public TagCreateCommand toCommand() {
        return new TagCreateCommand(name, attribute);
    }
}
