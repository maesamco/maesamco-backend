package com.maesamco.content.application.input_port;

import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.entity.Tag;

import java.util.List;
import java.util.UUID;

public interface ProblemTagFinder {

    /** 문제-태그 연결 존재 여부 조회 */
    boolean existsByProblemIdAndTagId(UUID problemId, UUID tagId);

    /** 특정 문제의 문제-태그 연결 목록 조회 */
    List<ProblemTag> getByProblemId(UUID problemId);

    /** 특정 문제에 연결된 태그 목록을 조회한다. */
    List<Tag> getTagsByProblemId(UUID problemId);

    /** 여러 문제에 연결된 태그 목록을 조회한다. */
    // TODO: 마지막에 작성, problemProgress 구현하고 이 함수와 함께 사용해
    //  (이미 원하는 조건으로 완성된 문제 list에 대하여 tag 집합을 반환하도록 한다.)
    // List<Tag> getTagsByProblemIds(List<UUID> problemIds);
}