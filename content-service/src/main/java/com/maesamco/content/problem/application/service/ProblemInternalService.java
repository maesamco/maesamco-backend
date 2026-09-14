package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.application.port.ProblemTagFinder;
import com.maesamco.content.problem.application.port.ProblemVersionFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.presentation.dto.response.InternalProblemResponse;
import com.maesamco.content.tag.domain.entity.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 내부 서비스용 문제 조회 서비스 */
@Service
@RequiredArgsConstructor
public class ProblemInternalService {

    private final ProblemFinder problemFinder;
    private final ProblemTagFinder problemTagFinder;
    private final ProblemVersionFinder problemVersionFinder;

    /** 내부 서비스용 문제 단건 조회 */
    @Transactional(readOnly = true)
    public InternalProblemResponse getProblemMetaData(UUID problemId) {

        // problem 정보
        Problem problem = problemFinder.getProblem(problemId);

        // problem에 해당하는 tag 리스트
        List<Tag> tags = problemTagFinder.getTagsByProblemId(problemId);

        return InternalProblemResponse.from(problem, tags);
    }

    /**
     * 내부 서비스용 문제 버전 단건 조회 — 제출 시점 문제 버전 기준으로 지문을 조회해야
     * 하는 호출자(Coaching Service)를 위한 것이다(이슈 #178). 개념 태그는 버전 스냅샷에
     * 없어 problemId 기준 현재 태그를 그대로 쓴다(InternalProblemResponse.fromVersion() 참고).
     */
    @Transactional(readOnly = true)
    public InternalProblemResponse getProblemVersionMetaData(UUID problemVersionId) {

        ProblemVersion problemVersion = problemVersionFinder.getProblemVersion(problemVersionId);

        List<Tag> tags = problemTagFinder.getTagsByProblemId(problemVersion.getProblemId());

        return InternalProblemResponse.fromVersion(
                problemVersion.getProblemId(),
                problemVersion.getProblemSnapshot().path("description").asText(),
                tags
        );
    }
}