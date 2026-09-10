package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.application.port.ProblemTagFinder;
import com.maesamco.content.problem.domain.entity.Problem;
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

    /** 내부 서비스용 문제 단건 조회 */
    @Transactional(readOnly = true)
    public InternalProblemResponse getProblemMetaData(UUID problemId) {

        // problem 정보
        Problem problem = problemFinder.getProblem(problemId);

        // problem에 해당하는 tag 리스트
        List<Tag> tags = problemTagFinder.getTagsByProblemId(problemId);

        return InternalProblemResponse.from(problem, tags);
    }
}