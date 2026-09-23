package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.command.ProblemCreateCommand;
import com.maesamco.content.application.command.ProblemUpdateCommand;
import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.query.ProblemSearchQuery;
import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.repository.problem.ProblemCommandRepository;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
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

    // Problem 한정 CQRS 도입
    private final ProblemCommandRepository problemCommandRepository;
    private final ProblemQueryRepository problemQueryRepository;

    private final ProblemVersionRepository problemVersionRepository;
    private final ProblemFinder problemFinder;
    private final ProblemPublicationFacade problemPublicationFacade;

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

        Problem savedProblem = problemCommandRepository.save(problem);

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

        Problem problem = problemFinder.getById(problemId);

        return ProblemResult.from(problem);
    }

    /** 사용자 문제 단건 조회 */
    @Transactional(readOnly = true)
    public ProblemResult getProblemForUser(UUID problemId) {

        Problem problem = problemFinder.getById(problemId);

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

        Page<Problem> problems = problemQueryRepository.searchProblems(query.toCondition(), pageable);

        return problems.map(ProblemSearchResult::from);
    }

    /** 문제 수정 */
    @Transactional(rollbackFor = Exception.class)
    public ProblemResult updateProblem(
            UUID problemId,
            ProblemUpdateCommand command
    ) {

        Problem problem = problemFinder.lockById(problemId);

        // 관리자가 조회했던 버전과 현재 DB 버전이 다르면
        // 오래된 데이터를 기준으로 한 수정 요청이므로 거부한다.
        if (!command.getLockVersion().equals(problem.getLockVersion())) {
            throw new BusinessException(
                    ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY
            );
        }

        boolean isModified =
                command.getTitle() != null
                        || command.getLanguage() != null
                        || command.getDifficulty() != null
                        || command.getType() != null
                        || command.getDescription() != null
                        || command.getStarterCode().isDefined()
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
        if (command.getStarterCode().isDefined()) {
            problem.changeStarterCode(
                    command.getStarterCode().getValue()
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

            // ⚠️ 이슈 #254 — PUBLISHED 상태의 문제에서 채점에 실제로 영향을 주는
            // 필드(language, runningTimeLimit, runningMemoryLimit)가 바뀌면,
            // judge-service의 실행 스펙(p_problem_execution_specs)이 낡은 채로
            // 남아 두 서비스 데이터가 조용히 어긋나는 문제가 있었다. 이 경우
            // 일반 snapshot() 대신 승인된 테스트케이스까지 포함한 발행 버전을
            // 새로 만들어 ProblemPublished 이벤트를 재발행한다.
            //
            // 반드시 양자택일이어야 한다 — 같은 currentVersionNo로 ProblemVersion을
            // 두 번(snapshot() 한 번, createPublished() 한 번) 저장하면
            // UNIQUE(problem_id, version_no) 제약 위반이 발생한다.
            boolean gradingCriticalFieldChanged =
                    command.getLanguage() != null
                            || command.getRunningTimeLimit() != null
                            || command.getRunningMemoryLimit() != null;

            if (problem.getProblemStatus() == ProblemStatus.PUBLISHED && gradingCriticalFieldChanged) {
                problemPublicationFacade.republishExistingVersion(problem);
            } else {
                /*
                 * 수정된 문제 상태를 증가된 currentVersionNo에 해당하는
                 * 새 버전 스냅샷으로 저장합니다.
                 */
                ProblemVersion snapshot = ProblemVersion.snapshot(problem);

                problemVersionRepository.save(snapshot);
            }

            // 응답을 생성하기 전에 UPDATE를 실행하여 JPA @Version 충돌 여부와 증가된 lockVersion을 확정한다.
            problemCommandRepository.flush();
        }

        return ProblemResult.from(problem);
    }

    /** 문제 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblem(UUID problemId, UUID userId) {

        Problem problem = problemFinder.lockById(problemId);

        problem.softDelete(userId);

        problemCommandRepository.flush(); // 현재 영속성 컨텍스트에 쌓여 있는 변경사항을 즉시 DB SQL로 반영시킨다
    }
}
