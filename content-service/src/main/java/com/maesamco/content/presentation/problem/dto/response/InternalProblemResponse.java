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
        return new InternalProblemResponse(
                problem.getId(),
                problem.getDescription(),
                conceptTagNames(tags)
        );
    }

    /**
     * 문제 버전 스냅샷과 태그 정보를 내부 서비스용 응답 DTO로 변환합니다.
     *
     * 개념 태그는 버전 스냅샷에 포함되지 않으므로(도메인 모델 한계),
     * problemId 기준 현재 태그를 그대로 사용합니다 — 지문(description)만
     * 제출 시점 버전 기준입니다.
     */
    public static InternalProblemResponse fromVersion(
            UUID problemId,
            String versionDescription,
            List<Tag> tags
    ) {
        return new InternalProblemResponse(
                problemId,
                versionDescription,
                conceptTagNames(tags)
        );
    }

    private static List<String> conceptTagNames(List<Tag> tags) {
        List<String> conceptTags = new ArrayList<>();

        for (Tag tag : tags) {
            if (tag.getAttribute() == TagAttribute.CONCEPT) {
                conceptTags.add(tag.getName());
            }
        }

        return conceptTags;
    }
}
