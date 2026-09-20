package com.maesamco.content.application.command_service;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.port.ProblemPublishedEventData;
import com.maesamco.content.application.port.ProblemPublishedEventPort;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionTestCaseItem;
import com.maesamco.content.domain.repository.TestCaseRepository;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 관리자의 문제 발행 승인을 처리하는 애플리케이션 서비스입니다.
 *
 * <p>문제 상태 전이, 발행 버전 스냅샷 생성,
 * ProblemPublished 이벤트 기록을 하나의 트랜잭션으로 처리합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProblemPublicationService {

    private final ProblemFinder problemFinder;
    private final TestCaseRepository testCaseRepository;
    private final ProblemVersionRepository problemVersionRepository;
    private final ProblemPublishedEventPort problemPublishedEventPort;

    /**
     * 관리자 문제 발행 승인을 처리합니다.
     *
     * <p>REVIEW_PENDING 상태의 문제를 PUBLISHED 상태로 전환하고,
     * 발행 시점의 문제와 승인된 테스트케이스를 ProblemVersion으로 고정한 뒤
     * ProblemPublished 이벤트를 기록합니다.</p>
     *
     * <p>이 과정 중 하나라도 실패하면 트랜잭션 전체가 롤백됩니다.</p>
     *
     * @param problemId 발행을 승인할 문제 식별자
     */
    @Transactional(rollbackFor = Exception.class)
    public void approvePublication(UUID problemId) {
        Problem problem = problemFinder.getById(problemId);

        List<TestCase> testCases = testCaseRepository
                .findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
                    problemId,
                    TestCaseStatus.APPROVED
            );

        /*
         * 승인된 테스트케이스가 하나도 없는 문제는
         * Judge Service에서 실행 가능한 채점 기준을 만들 수 없으므로 발행하지 않습니다.
         */
        if (testCases.isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_PUBLICATION_TEST_CASE_REQUIRED);
        }

        // REVIEW_PENDING 상태인지 검증하면서 PUBLISHED 상태로 전환합니다.
        problem.approvePublication();

        /*
         * 생성/수정 시의 기존 버전과 중복되지 않도록
         * 발행 승인도 새로운 문제 버전으로 기록합니다.
         */
        problem.increaseVersion();

        Instant publicationTime = Instant.now();

        List<ProblemVersionTestCaseItem> versionTestCases = testCases.stream()
                .map(ProblemVersionTestCaseItem::from)
                .toList();

        ProblemVersion problemVersion = ProblemVersion.createPublished(
                problem,
                versionTestCases,
                publicationTime
        );

        ProblemVersion savedProblemVersion = problemVersionRepository.save(problemVersion);
        UUID problemVersionId = savedProblemVersion.getId();

        if (problemVersionId == null) {
            throw new IllegalStateException("ProblemVersion id was not generated");
        }

        ProblemPublishedEventData eventData = new ProblemPublishedEventData(
                UUID.randomUUID(),
                publicationTime,
                problemVersionId,
                savedProblemVersion
        );

        problemPublishedEventPort.record(eventData);
    }
}