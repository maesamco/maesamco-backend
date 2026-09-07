package com.maesamco.content.tag.application.port;

import com.maesamco.content.tag.domain.entity.ProblemTag;

import java.util.List;
import java.util.UUID;

public interface ProblemTagFinder {

    /** 문제-태그 연결 존재 여부 조회 */
    boolean existsProblemTag(UUID problemId, UUID tagId);

    /** 특정 문제의 문제-태그 연결 목록 조회 */
    List<ProblemTag> getProblemTags(UUID problemId);
}