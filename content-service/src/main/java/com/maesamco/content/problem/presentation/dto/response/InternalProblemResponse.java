package com.maesamco.content.problem.presentation.dto.response;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 내부 서비스용 문제 조회 응답 DTO */
@Getter
@AllArgsConstructor
public class InternalProblemResponse {

    /**
     * Coaching Service가 ProblemSnapshot.problemId로 매핑하는 필드입니다.
     * 외부 응답 필드명은 기존 계약대로 id를 유지합니다.
     */
    private final UUID id;

    private final String description;

    private final List<String> conceptTags;

    /** 문제와 태그 정보를 내부 서비스용 응답 DTO로 변환합니다. */
    public static InternalProblemResponse from(
            Problem problem,
            List<Tag> tags
    ) {
        List<String> conceptTags = new ArrayList<>();

        for (Tag tag : tags) {
            if (tag.getAttribute() == TagAttribute.CONCEPT) {
                conceptTags.add(tag.getName());
            }
        }

        return new InternalProblemResponse(
                problem.getId(),
                problem.getDescription(),
                conceptTags
        );
    }
}
