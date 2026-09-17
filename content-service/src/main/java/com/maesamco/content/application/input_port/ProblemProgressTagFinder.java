package com.maesamco.content.application.input_port;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;

import java.util.List;
import java.util.UUID;

/** ProblemProgressFinder 와 ProblemTagFinder 를 결합한 Finder */
public interface ProblemProgressTagFinder {

    // TODO: 마지막에 작업
    /** 사용자의 전체 문제 풀이 이력을 기준으로 중복 없는 태그 목록을 조회합니다. */
    List<Tag> getTagsByUserId(UUID userId);

    /** 사용자의 특정 문제 풀이 상태를 기준으로 중복 없는 태그 목록을 조회합니다. */
    List<Tag> getTagsByUserIdAndProgressStatus(UUID userId, ProblemProgressStatus progressStatus);
}