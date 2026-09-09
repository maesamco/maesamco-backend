package com.maesamco.content.problem.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.problem.presentation.dto.request.ProblemCreateRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemCreateResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemSearchItemResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemShortResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 문제 생성, 조회, 수정, 삭제를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    // private final ProblemVersionRepository problemVersionRepository;
    private final ProblemFinder problemFinder;

    /** 문제 생성 */
    @Transactional(rollbackFor = Exception.class)
    public ProblemCreateResponse createProblem(ProblemCreateRequest request) {

        Problem problem = Problem.create(
                request.getTitle(),
                request.getLanguage(),
                request.getDifficulty(),
                request.getType(),
                request.getDescription(),
                request.getStarterCode(),
                request.getRunningTimeLimit(),
                request.getRunningMemoryLimit(),
                request.getTimerPolicy(),
                request.getSource(),
                ProblemStatus.DRAFT
        );

        // TODO: 현재의 로직은 관리자가 생성한 문제는 검증을 거치지 않고 발행된다. 나중에 따로 흐름을 추가할 수도 있다.
        problem.setProblemStatusReviewPending();

        Problem savedProblem = problemRepository.save(problem);

        return ProblemCreateResponse.from(savedProblem);
    }

    /** 관리자 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemResponse getProblemForAdmin(UUID problemId) {

        Problem problem = problemFinder.getProblem(problemId);

        return ProblemResponse.from(problem);
    }

    /** 사용자 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemShortResponse getProblemForUser(UUID problemId) {

        Problem problem = problemFinder.getProblem(problemId);

        // 사용자는 발행된 문제만 조회 가능
        if (problem.getProblemStatus() != ProblemStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_PUBLISHED);
        }

        return ProblemShortResponse.from(problem);
    }

    /** 문제 검색 */
    @Transactional(readOnly = true)
    public PageResponse<ProblemSearchItemResponse> searchProblems(ProblemSearchRequest request, Pageable pageable) {

        // TODO: 시연에서는 의도한 흐름대로 진행하니 문제는 안생기나
        //  추후 컨트롤러에서 .getRole() 이 가능하다면 사용자 / 관리자 관련 정책을 도입

        // problem status가 안 들어왔으면 ProblemStatus.PUBLISHED 인 문제만 검색
        if (request.getProblemStatus() == null) {
            request.setProblemStatus(ProblemStatus.PUBLISHED);
        }

        Page<Problem> problems = problemRepository.searchProblems(request, pageable);

        return PageResponse.from(problems, ProblemSearchItemResponse::from);
    }

    /** 문제 수정 */
    @Transactional(rollbackFor = Exception.class)
    public ProblemResponse updateProblem(UUID problemId, ProblemUpdateRequest request) {

        Problem problem = problemFinder.getProblem(problemId);

        // TODO: 수정하기 전에 version snapshot 남기기 (다른 브랜치에서 작업한 것과 merge해야 활성화할 수 있음)
        // ProblemVersion snapshot = ProblemVersion.snapshot(problem);
        // problemVersionRepository.save(snapshot);

        boolean is_modified = false;

        // 수정 요청이 있는 값들만 수정
        if (request.getTitle() != null) {
            problem.changeTitle(request.getTitle());
            is_modified = true;
        }
        if (request.getLanguage() != null) {
            problem.changeLanguage(request.getLanguage());
            is_modified = true;
        }
        if (request.getDifficulty() != null) {
            problem.changeDifficulty(request.getDifficulty());
            is_modified = true;
        }
        if (request.getType() != null) {
            problem.changeType(request.getType());
            is_modified = true;
        }
        if (request.getDescription() != null) {
            problem.changeDescription(request.getDescription());
            is_modified = true;
        }


        // JsonNullable 객체의 내부 함수를 사용하려면 not null이어야 한다.
        if (request.getStarterCode() == null) {
            throw new BusinessException(ErrorCode.STARTER_CODE_NOT_INITIALIZED);
        }
        // 들어왔는데 null인 경우 -> 기존값을 null / 안 들어와서 null인 경우 -> 안 바꿈
        if (request.getStarterCode().isPresent()) {
            problem.changeStarterCode(
                    request.getStarterCode().orElse(null)
            );
            is_modified = true;
        }

        if (request.getRunningTimeLimit() != null) {
            problem.changeRunningTimeLimit(request.getRunningTimeLimit());
            is_modified = true;
        }
        if (request.getRunningMemoryLimit() != null) {
            problem.changeRunningMemoryLimit(request.getRunningMemoryLimit());
            is_modified = true;
        }
        if (request.getTimerPolicy() != null) {
            problem.changeTimerPolicy(request.getTimerPolicy());
            is_modified = true;
        }
        if (request.getSource() != null) {
            problem.changeSource(request.getSource());
            is_modified = true;
        }
        if (is_modified) {
            problem.increaseVersion();
        }

        return ProblemResponse.from(problem);
    }

    /** 문제 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblem(UUID problemId, UUID userId) {

        Problem problem = problemFinder.getProblem(problemId);

        problem.softDelete(userId);
    }
}