package com.maesamco.content.application.facade;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.finder.TestCaseFinder;
import com.maesamco.content.application.port.ProblemPublishedEventData;
import com.maesamco.content.application.port.ProblemPublishedEventPort;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionTestCaseItem;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
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
        // ⚠️ 상태 전환(problem.approvePublication()) 전에 검증해야 합니다 — 검증
        // 실패 시 문제 상태가 REVIEW_PENDING으로 유지되어야 하기 때문입니다.
        if (testCases.isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_PUBLICATION_TEST_CASE_REQUIRED);
        }

        // 문제 상태를 REVIEW_PENDING에서 PUBLISHED로 전환합니다.
        problem.approvePublication();

        // 이미 조회한 테스트케이스로 새 발행 버전 + 발행 이벤트를 생성합니다.
        publishNewVersionAndEvent(problem, testCases);
    }

    /**
     * 이미 PUBLISHED 상태인 문제에 대해, 새 ProblemVersion(테스트케이스 포함
     * 스냅샷)과 새 ProblemPublished 이벤트를 발행합니다. 상태 전환은 하지
     * 않습니다 — 이슈 #254처럼 이미 PUBLISHED인 문제의 채점 관련 필드가
     * 수정됐을 때, judge-service와의 정합성을 맞추기 위해 호출됩니다.
     *
     * <p>⚠️ 호출자가 이미 같은 트랜잭션 안에서 락을 걸고 조회한 {@link Problem}을
     * 그대로 전달해야 합니다 — 이 메서드는 재조회하지 않으며,
     * {@code Propagation.MANDATORY}로 반드시 기존 트랜잭션 안에서만 호출
     * 가능하도록 강제합니다(트랜잭션 없이 호출되면 즉시 예외).</p>
     *
     * @throws BusinessException problem이 PUBLISHED 상태가 아니거나, 승인된
     *                            테스트케이스가 없는 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void republishExistingVersion(Problem problem) {
        if (problem.getProblemStatus() != ProblemStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.INVALID_PROBLEM_STATUS_TRANSITION);
        }

        // 발행 버전에 포함할 승인된 테스트케이스를 조회합니다.
        List<TestCase> testCases = testCaseFinder.findApprovedTestCases(problem.getId());

        // 승인된 테스트케이스가 없는 문제는 발행할 수 없습니다.
        if (testCases.isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_PUBLICATION_TEST_CASE_REQUIRED);
        }

        publishNewVersionAndEvent(problem, testCases);
    }

    /** 새 발행 버전(테스트케이스 포함 스냅샷)을 저장하고 발행 이벤트를 기록하는 공통 로직입니다. */
    private void publishNewVersionAndEvent(Problem problem, List<TestCase> testCases) {
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

        if (savedProblemVersion.getId() == null) {
            throw new IllegalStateException("ProblemVersion id was not generated");
        }

        // 저장된 발행 버전을 기반으로 ProblemPublished 이벤트 데이터를 생성합니다.
        ProblemPublishedEventData eventData = ProblemPublishedEventData.create(publicationTime, savedProblemVersion);

        // ProblemPublished 이벤트를 Outbox에 기록합니다.
        problemPublishedEventPort.record(eventData);
    }

    /**
     * ⚠️ 이슈 #253 — 발행된 문제를 되돌리거나 재발행할 방법이 없던 문제 해결.
     *
     * <p>ProblemPublished 이벤트가 어떤 이유로든(버그, Kafka 장애 등) 다른
     * 서비스에 정상 반영되지 못한 문제를, 삭제·재생성 없이 그대로 재발행할
     * 수 있도록 PUBLISHED 상태를 REVIEW_PENDING으로 되돌립니다. 이후 관리자가
     * 기존 승인 API({@link #approvePublication(UUID)})를 다시 호출하면, 새
     * ProblemVersion과 새 ProblemPublished 이벤트가 생성되어 재발행됩니다.</p>
     *
     * <p>이 메서드 자체는 문제 콘텐츠나 테스트케이스를 전혀 건드리지 않고
     * 상태 전환만 수행합니다 — 재발행 전에 콘텐츠 수정이 필요하다면 별도
     * 수정 API를 통해 상태 전환과 무관하게 처리합니다.</p>
     *
     * <p>⚠️ 참고(P4, 비차단): REVIEW_PENDING으로 되돌린 시점부터 다시
     * {@link #approvePublication(UUID)}로 PUBLISHED가 될 때까지, 이 문제는
     * 공개 검색·목록 조회({@code searchProblems}의 강제 PUBLISHED 필터)에서
     * 잠깐 빠집니다. 관리자가 곧바로 재승인할 것을 전제로 한 짧은 창이라
     * 문제로 보지 않지만, 재발행 API를 호출하고 오래 방치하면 그 시간만큼
     * 사용자에게 노출되지 않는 상태가 지속된다는 점은 인지하고 있어야
     * 합니다.</p>
     */
    @Transactional
    public void revertToReviewPendingForRepublish(UUID problemId) {
        Problem problem = problemFinder.lockById(problemId);
        problem.revertToReviewPendingForRepublish();
    }
}