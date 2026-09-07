package com.maesamco.content.problem.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.problem.domain.repository.ProblemVersionRepository;
import com.maesamco.content.problem.presentation.dto.request.ProblemCreateRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemCreateResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemSearchItemResponse;
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
    private final ProblemVersionRepository problemVersionRepository;
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
                request.getRunningTimeLimit().getSeconds(),
                request.getRunningMemoryLimit().getMegabytes(),
                request.getTimerPolicy(),
                request.getSource(),
                request.getProblemStatus(),
                1
        );

        // TODO: 현재의 로직은 관리자가 생성한 문제는 검증을 거치지 않고 발행된다. 나중에 따로 흐름을 추가할 수도 있다.
        problem.changeProblemStatus(ProblemStatus.PUBLISHED);

        Problem savedProblem = problemRepository.save(problem);

        return ProblemCreateResponse.from(savedProblem);
    }

    /** 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemResponse getProblem(UUID problemId) {

        Problem problem = problemFinder.getProblem(problemId);

        return ProblemResponse.from(problem);
    }

    /** 문제 검색 */
    @Transactional(readOnly = true)
    public PageResponse<ProblemSearchItemResponse> searchProblems(ProblemSearchRequest request, Pageable pageable) {

        Page<Problem> problems = problemRepository.searchProblems(request, pageable);

        return PageResponse.from(problems, ProblemSearchItemResponse::from);
    }

    /** 문제 수정 */
    @Transactional(rollbackFor = Exception.class)
    public ProblemResponse updateProblem(UUID problemId, ProblemUpdateRequest request) {

        Problem problem = problemFinder.getProblem(problemId);

        // JsonNullable 객체의 내부 함수를 사용하려면 not null이어야 한다.
        if (request.getStarterCode() == null) {
            throw new BusinessException(ErrorCode.STARTER_CODE_NOT_INITIALIZED);
        }

        // TODO: 문제 버전 저장에 대해 얕은 영향은 그대로, 깊은 영향은 kafka에서 다시 수집하고 새로운 버전으로 등록할 지 결정
        //  [ex) startCode는 바뀌었다고 문제에 지장이 안 가서 문제 버전을 바꾸기 모호할 수 있음]
        boolean isModified =
                request.getTitle() != null ||
                        request.getLanguage() != null ||
                        request.getDifficulty() != null ||
                        request.getType() != null ||
                        request.getDescription() != null ||
                        request.getStarterCode().isPresent() ||
                        request.getRunningTimeLimit() != null ||
                        request.getRunningMemoryLimit() != null ||
                        request.getTimerPolicy() != null ||
                        request.getSource() != null ||
                        request.getProblemStatus() != null;

        if (isModified) {
            // 수정하기 전에 version snapshot 남기기
            ProblemVersion snapshot = ProblemVersion.snapshot(problem);
            problemVersionRepository.save(snapshot);
        }

        // 수정 요청이 있는 값들만 수정
        if (request.getTitle() != null) {
            problem.changeTitle(request.getTitle());
        }
        if (request.getLanguage() != null) {
            problem.changeLanguage(request.getLanguage());
        }
        if (request.getDifficulty() != null) {
            problem.changeDifficulty(request.getDifficulty());
        }
        if (request.getType() != null) {
            problem.changeType(request.getType());
        }
        if (request.getDescription() != null) {
            problem.changeDescription(request.getDescription());
        }
        // 들어왔는데 null인 경우 -> 기존값을 null / 안 들어와서 null인 경우 -> 안 바꿈
        if (request.getStarterCode().isPresent()) {
            problem.changeStarterCode(
                    request.getStarterCode().orElse(null)
            );
        }
        if (request.getRunningTimeLimit() != null) {
            problem.changeRunningTimeLimit(request.getRunningTimeLimit().getSeconds());
        }
        if (request.getRunningMemoryLimit() != null) {
            problem.changeRunningMemoryLimit(request.getRunningMemoryLimit().getMegabytes());
        }
        if (request.getTimerPolicy() != null) {
            problem.changeTimerPolicy(request.getTimerPolicy());
        }
        if (request.getSource() != null) {
            problem.changeSource(request.getSource());
        }
        if (request.getProblemStatus() != null) {
            problem.changeProblemStatus(request.getProblemStatus());
        }

        if (isModified) {
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