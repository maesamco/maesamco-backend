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
import com.maesamco.content.problem.presentation.dto.response.ProblemShortResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    public ProblemCreateResponse createProblem(
            ProblemCreateRequest request
    ) {
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

        problem.setProblemStatusReviewPending();

        Problem savedProblem =
                problemRepository.save(problem);

        /*
         * 생성된 문제의 현재 버전은 1이므로
         * 최초 상태를 version 1 스냅샷으로 저장합니다.
         */
        ProblemVersion initialVersion =
                ProblemVersion.snapshot(savedProblem);

        problemVersionRepository.save(initialVersion);

        return ProblemCreateResponse.from(savedProblem);
    }

    /** 관리자 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemResponse getProblemForAdmin(
            UUID problemId
    ) {
        Problem problem =
                problemFinder.getProblem(problemId);

        return ProblemResponse.from(problem);
    }

    /** 사용자 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemShortResponse getProblemForUser(
            UUID problemId
    ) {
        Problem problem =
                problemFinder.getProblem(problemId);

        if (problem.getProblemStatus()
                != ProblemStatus.PUBLISHED) {
            throw new BusinessException(
                    ErrorCode.PROBLEM_NOT_PUBLISHED
            );
        }

        return ProblemShortResponse.from(problem);
    }

    /** 공개 문제 검색 */
    @Transactional(readOnly = true)
    public PageResponse<ProblemSearchItemResponse> searchProblems(
            ProblemSearchRequest request,
            Pageable pageable
    ) {
        /*
         * 공개 목록에서는 클라이언트 요청과 관계없이
         * PUBLISHED 상태 문제만 조회합니다.
         */
        request.setProblemStatus(
                ProblemStatus.PUBLISHED
        );

        Page<Problem> problems =
                problemRepository.searchProblems(
                        request,
                        pageable
                );

        return PageResponse.from(
                problems,
                ProblemSearchItemResponse::from
        );
    }

    /** 문제 수정 */
    @Transactional(rollbackFor = Exception.class)
    public ProblemResponse updateProblem(
            UUID problemId,
            ProblemUpdateRequest request
    ) {
        Problem problem =
                problemFinder.getProblem(problemId);

        /*
         * 관리자가 조회한 버전과 현재 DB 버전이 다르면
         * 오래된 데이터를 기준으로 한 요청이므로 거부합니다.
         */
        if (request.getLockVersion() == null
                || !request.getLockVersion()
                .equals(problem.getLockVersion())) {
            throw new BusinessException(
                    ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY
            );
        }

        /*
         * JsonNullable 객체 자체가 null이면
         * 부분 수정 여부를 안전하게 판단할 수 없습니다.
         */
        if (request.getStarterCode() == null) {
            throw new BusinessException(
                    ErrorCode.STARTER_CODE_NOT_INITIALIZED
            );
        }

        boolean isModified =
                request.getTitle() != null
                        || request.getLanguage() != null
                        || request.getDifficulty() != null
                        || request.getType() != null
                        || request.getDescription() != null
                        || request.getStarterCode().isPresent()
                        || request.getRunningTimeLimit() != null
                        || request.getRunningMemoryLimit() != null
                        || request.getTimerPolicy() != null
                        || request.getSource() != null;

        if (request.getTitle() != null) {
            problem.changeTitle(
                    request.getTitle()
            );
        }

        if (request.getLanguage() != null) {
            problem.changeLanguage(
                    request.getLanguage()
            );
        }

        if (request.getDifficulty() != null) {
            problem.changeDifficulty(
                    request.getDifficulty()
            );
        }

        if (request.getType() != null) {
            problem.changeType(
                    request.getType()
            );
        }

        if (request.getDescription() != null) {
            problem.changeDescription(
                    request.getDescription()
            );
        }

        /*
         * 명시적인 null은 starterCode 제거로 처리하고,
         * 필드가 전달되지 않았으면 기존 값을 유지합니다.
         */
        if (request.getStarterCode().isPresent()) {
            problem.changeStarterCode(
                    request.getStarterCode()
                            .orElse(null)
            );
        }

        if (request.getRunningTimeLimit() != null) {
            problem.changeRunningTimeLimit(
                    request.getRunningTimeLimit()
            );
        }

        if (request.getRunningMemoryLimit() != null) {
            problem.changeRunningMemoryLimit(
                    request.getRunningMemoryLimit()
            );
        }

        if (request.getTimerPolicy() != null) {
            problem.changeTimerPolicy(
                    request.getTimerPolicy()
            );
        }

        if (request.getSource() != null) {
            problem.changeSource(
                    request.getSource()
            );
        }

        if (isModified) {
            /*
             * 변경을 적용한 뒤 버전을 증가시키고,
             * 증가한 버전 번호에 대응하는 새로운 상태를 저장합니다.
             */
            problem.increaseVersion();

            try {
                ProblemVersion snapshot =
                        ProblemVersion.snapshot(problem);

                problemVersionRepository.save(snapshot);

                /*
                 * 응답 전에 UPDATE를 실행하여 JPA @Version 충돌과
                 * 증가된 lockVersion을 확정합니다.
                 */
                problemRepository.flush();
            } catch (
                    ObjectOptimisticLockingFailureException exception
            ) {
                throw new BusinessException(
                        ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY
                );
            }
        }

        return ProblemResponse.from(problem);
    }

    /** 문제 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblem(
            UUID problemId,
            UUID userId
    ) {
        Problem problem =
                problemFinder.getProblem(problemId);

        problem.softDelete(userId);
    }
}
