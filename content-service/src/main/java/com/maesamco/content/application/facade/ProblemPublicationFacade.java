package com.maesamco.content.application.facade;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.finder.TestCaseFinder;
import com.maesamco.content.application.port.ProblemPublishedEventData;
import com.maesamco.content.application.port.ProblemPublishedEventPort;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionTestCaseItem;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProblemPublicationFacade {

    private final ProblemFinder problemFinder;
    private final TestCaseFinder testCaseFinder;
    private final ProblemVersionRepository problemVersionRepository;
    private final ProblemPublishedEventPort problemPublishedEventPort;

    @Transactional
    public void approvePublication(UUID problemId) {

        // 발행 대상 문제를 비관적 락으로 조회합니다.
        Problem problem = problemFinder.lockById(problemId);

        // 발행 버전에 포함할 승인된 테스트케이스를 조회합니다.
        List<TestCase> testCases = testCaseFinder.findApprovedTestCases(problemId);

        // 승인된 테스트케이스가 없는 문제는 발행할 수 없습니다.
        if (testCases.isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_PUBLICATION_TEST_CASE_REQUIRED);
        }

        // 문제 상태를 REVIEW_PENDING에서 PUBLISHED로 전환합니다.
        problem.approvePublication();

        // 버전 스냅샷과 발행 이벤트에서 사용할 발행 시각을 고정합니다.
        Instant publicationTime = Instant.now();

        // 테스트케이스를 발행 버전에 저장할 스냅샷 형태로 변환합니다.
        List<ProblemVersionTestCaseItem> versionTestCases = testCases.stream()
                .map(ProblemVersionTestCaseItem::from)
                .toList();

        // 발행 시점의 문제와 테스트케이스를 ProblemVersion으로 생성합니다.
        ProblemVersion problemVersion = ProblemVersion.createPublished(problem, versionTestCases, publicationTime);

        // 생성한 발행 버전을 저장합니다.
        ProblemVersion savedProblemVersion = problemVersionRepository.save(problemVersion);

        // 저장된 발행 버전을 기반으로 ProblemPublished 이벤트 데이터를 생성합니다.
        ProblemPublishedEventData eventData = ProblemPublishedEventData.create(publicationTime, savedProblemVersion);

        // ProblemPublished 이벤트를 Outbox에 기록합니다.
        problemPublishedEventPort.record(eventData);
    }
}