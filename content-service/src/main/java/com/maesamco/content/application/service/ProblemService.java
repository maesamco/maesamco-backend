package com.maesamco.content.application.service;

import com.maesamco.content.application.command.ProblemCreateCommand;
import com.maesamco.content.application.command.ProblemUpdateCommand;
import com.maesamco.content.application.input_port.ProblemFinder;
import com.maesamco.content.application.query.ProblemSearchQuery;
import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.repository.problem.ProblemRepository;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
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
    public ProblemResult createProblem(ProblemCreateCommand command) {
        Problem problem = Problem.create(
                command.getTitle(),
                command.getLanguage(),
                command.getDifficulty(),
                command.getType(),
                command.getDescription(),
                command.getStarterCode(),
                command.getRunningTimeLimit(),
                command.getRunningMemoryLimit(),
                command.getTimerPolicy(),
                command.getSource(),
                ProblemStatus.DRAFT
        );

        problem.requestPublicationReview();

        Problem savedProblem = problemRepository.save(problem);

        /*
         * 생성된 문제의 현재 버전은 1이므로
         * 최초 상태를 version 1 스냅샷으로 저장합니다.
         */
        ProblemVersion initialVersion = ProblemVersion.snapshot(savedProblem);

        problemVersionRepository.save(initialVersion);

        return ProblemResult.from(savedProblem);
    }

    /** 관리자 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemResult getProblemForAdmin(UUID problemId) {

        Problem problem = problemFinder.getProblem(problemId);

        return ProblemResult.from(problem);
    }

    /** 사용자 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemResult getProblemForUser(UUID problemId) {

        Problem problem = problemFinder.getProblem(problemId);

        // 사용자는 발행된 문제만 조회 가능
        if (problem.getProblemStatus() != ProblemStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND); // user enumeration oracle 방지를 위해 에러 통일
        }

        return ProblemResult.from(problem);
    }

    /** 문제 검색 */
    @Transactional(readOnly = true)
    public Page<ProblemSearchResult> searchProblems(
            ProblemSearchQuery query,
            Pageable pageable
    ) {

        // 공개 문제 목록에서는 클라이언트가 요청한 상태와 관계없이 PUBLISHED 상태의 문제만 조회한다.
        query.forcePublished();

        Page<Problem> problems = problemRepository.searchProblems(query.toCondition(), pageable);

        return problems.map(ProblemSearchResult::from);
    }

    /** 문제 수정 */
    @Transactional(rollbackFor = Exception.class)
    public ProblemResult updateProblem(
            UUID problemId,
            ProblemUpdateCommand command
    ) {

        Problem problem = problemFinder.getProblem(problemId);

        // 관리자가 조회했던 버전과 현재 DB 버전이 다르면
        // 오래된 데이터를 기준으로 한 수정 요청이므로 거부한다.
        if (!command.getLockVersion().equals(problem.getLockVersion())) {
            throw new BusinessException(
                    ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY
            );
        }

        // JsonNullable 객체의 내부 함수를 사용하려면 not null이어야 한다.
        if (command.getStarterCode() == null) {
            throw new BusinessException(
                    ErrorCode.STARTER_CODE_NOT_INITIALIZED
            );
        }

        boolean isModified =
                command.getTitle() != null
                        || command.getLanguage() != null
                        || command.getDifficulty() != null
                        || command.getType() != null
                        || command.getDescription() != null
                        || command.getStarterCode().isPresent()
                        || command.getRunningTimeLimit() != null
                        || command.getRunningMemoryLimit() != null
                        || command.getTimerPolicy() != null
                        || command.getSource() != null;

        // 수정 요청이 있는 값들만 수정
        if (command.getTitle() != null) {
            problem.changeTitle(command.getTitle());
        }
        if (command.getLanguage() != null) {
            problem.changeLanguage(command.getLanguage());
        }
        if (command.getDifficulty() != null) {
            problem.changeDifficulty(command.getDifficulty());
        }
        if (command.getType() != null) {
            problem.changeType(command.getType());
        }
        if (command.getDescription() != null) {
            problem.changeDescription(command.getDescription());
        }
        // 들어왔는데 null인 경우 -> 기존값을 null / 안 들어와서 null인 경우 -> 안 바꿈
        if (command.getStarterCode().isPresent()) {
            problem.changeStarterCode(
                    command.getStarterCode().orElse(null)
            );
        }
        if (command.getRunningTimeLimit() != null) {
            problem.changeRunningTimeLimit(command.getRunningTimeLimit());
        }
        if (command.getRunningMemoryLimit() != null) {
            problem.changeRunningMemoryLimit(command.getRunningMemoryLimit());
        }
        if (command.getTimerPolicy() != null) {
            problem.changeTimerPolicy(command.getTimerPolicy());
        }
        if (command.getSource() != null) {
            problem.changeSource(command.getSource());
        }

        if (isModified) {
            problem.increaseVersion();

            /*
             * 수정된 문제 상태를 증가된 currentVersionNo에 해당하는
             * 새 버전 스냅샷으로 저장합니다.
             */
            ProblemVersion snapshot = ProblemVersion.snapshot(problem);

            problemVersionRepository.save(snapshot);

            // 응답을 생성하기 전에 UPDATE를 실행하여 JPA @Version 충돌 여부와 증가된 lockVersion을 확정한다.
            problemRepository.flush();
        }

        return ProblemResult.from(problem);
    }

    /** 문제 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblem(UUID problemId, UUID userId) {

        Problem problem = problemFinder.getProblem(problemId);

        problem.softDelete(userId);

        problemRepository.flush(); // 현재 영속성 컨텍스트에 쌓여 있는 변경사항을 즉시 DB SQL로 반영시킨다
    }
}
