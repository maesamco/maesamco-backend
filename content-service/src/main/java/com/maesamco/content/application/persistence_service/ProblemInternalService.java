package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.finder.ProblemTagFinder;
import com.maesamco.content.application.finder.ProblemVersionFinder;
import com.maesamco.content.application.result.ProblemInternalResult;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.Tag;
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
    public ProblemInternalResult getProblemMetaData(UUID problemId) {

        // problem 정보
        Problem problem = problemFinder.getById(problemId);

        // problem에 해당하는 tag 리스트
        List<Tag> tags = problemTagFinder.getTagsByProblemId(problemId);

        return ProblemInternalResult.from(problem, tags);
    }

    /**
     * 내부 서비스용 문제 버전 단건 조회 — 제출 시점 문제 버전 기준으로 지문을 조회해야
     * 하는 호출자(Coaching Service)를 위한 것이다(이슈 #178). 개념 태그는 버전 스냅샷에
     * 없어 problemId 기준 현재 태그를 그대로 쓴다(ProblemInternalResult.fromVersion() 참고).
     */
    @Transactional(readOnly = true)
    public ProblemInternalResult getProblemVersionMetaData(UUID problemVersionId) {

        // 외부에서 Problem Version을 조회하는 경우, problemId - problemVersionId를 가지고 있다고 가정한다.
        // 그래서 따로 크로스 검증(problem에 대한 problemVersion인지) 하지 않는다.
        ProblemVersion problemVersion = problemVersionFinder.getById(problemVersionId);

        List<Tag> tags = problemTagFinder.getTagsByProblemId(problemVersion.getProblemId());

        return ProblemInternalResult.fromVersion(
                problemVersion.getProblemId(),
                problemVersion.getProblemSnapshot().path("description").asText(),
                tags
        );
    }
}