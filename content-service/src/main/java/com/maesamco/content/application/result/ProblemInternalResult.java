package com.maesamco.content.application.result;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.Problem;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 내부 서비스용 문제 조회 결과 */
@Getter
@AllArgsConstructor
public class ProblemInternalResult {

    private final UUID id;
    private final String description;
    private final List<String> conceptTags;

    /** 문제와 태그 정보를 내부 서비스용 조회 결과로 변환합니다. */
    public static ProblemInternalResult from(
            Problem problem,
            List<Tag> tags
    ) {
        return new ProblemInternalResult(
                problem.getId(),
                problem.getDescription(),
                conceptTagNames(tags)
        );
    }

    /** 문제 버전 스냅샷과 태그 정보를 내부 서비스용 조회 결과로 변환합니다. */
    public static ProblemInternalResult fromVersion(
            UUID problemId,
            String versionDescription,
            List<Tag> tags
    ) {
        return new ProblemInternalResult(
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