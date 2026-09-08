package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.domain.entity.TestCase;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import com.maesamco.content.problem.domain.repository.ProblemVersionRepository;
import com.maesamco.content.problem.domain.repository.TestCaseRepository;
import com.maesamco.content.problem.infrastructure.messaging.event.ProblemPublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 관리자의 문제 발행 승인을 처리하는 애플리케이션 서비스입니다.
 *
 * <p>문제 상태 전이, 발행 버전 스냅샷 생성,
 * ProblemPublished 이벤트 Outbox 저장을
 * 하나의 트랜잭션으로 처리합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProblemPublicationService {

    private final ProblemFinder problemFinder;

    private final TestCaseRepository testCaseRepository;

    private final ProblemVersionRepository problemVersionRepository;

    private final ProblemEventOutboxRepository problemEventOutboxRepository;

    private final JsonMapper jsonMapper;

    /**
     * 관리자 문제 발행 승인을 처리합니다.
     *
     * <p>REVIEW_PENDING 상태의 문제를 PUBLISHED 상태로 전환하고,
     * 발행 시점의 문제와 테스트케이스를 ProblemVersion으로 고정한 뒤
     * ProblemPublished 이벤트를 Outbox에 저장합니다.</p>
     *
     * <p>이 과정 중 하나라도 실패하면 트랜잭션 전체가 롤백됩니다.</p>
     *
     * @param problemId 발행을 승인할 문제 식별자
     */
    @Transactional
    public void approvePublication(
            UUID problemId
    ) {
        Problem problem =
                problemFinder.getProblem(
                        problemId
                );

        List<TestCase> testCases =
                testCaseRepository
                        .findAllByProblemIdOrderByDisplayOrderAsc(
                                problemId
                        );

        /*
         * REVIEW_PENDING 상태인지 도메인에서 검증하면서
         * PUBLISHED 상태로 전환합니다.
         *
         * 이후 작업이 실패하면 같은 트랜잭션 안에서
         * 이 상태 변경도 함께 롤백됩니다.
         */
        problem.approvePublication();

        Instant publicationTime =
                Instant.now();

        List<ProblemVersion.TestCaseItem> versionTestCases =
                testCases.stream()
                        .map(
                                this::toVersionTestCaseItem
                        )
                        .toList();

        ProblemVersion problemVersion =
                ProblemVersion.createPublished(
                        problemId,
                        problem,
                        versionTestCases,
                        publicationTime
                );

        ProblemVersion savedProblemVersion =
                problemVersionRepository.save(
                        problemVersion
                );

        UUID problemVersionId =
                savedProblemVersion.getId();

        if (problemVersionId == null) {
            throw new IllegalStateException(
                    "ProblemVersion id was not generated"
            );
        }

        UUID eventId =
                UUID.randomUUID();

        ProblemPublishedEvent event =
                ProblemPublishedEvent.fromPublishedVersion(
                        eventId,
                        publicationTime,
                        problemVersionId,
                        savedProblemVersion
                );

        String payload =
                serializeEvent(
                        event
                );

        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        event.eventId(),
                        event.problemId(),
                        event.eventVersion(),
                        payload,
                        event.occurredAt()
                );

        problemEventOutboxRepository.save(
                outbox
        );
    }

    /**
     * 영속 TestCase를 발행 버전 스냅샷 항목으로 변환합니다.
     */
    private ProblemVersion.TestCaseItem toVersionTestCaseItem(
            TestCase testCase
    ) {
        return new ProblemVersion.TestCaseItem(
                testCase.getId(),
                testCase.isPublic(),
                testCase.getInput(),
                testCase.getExpectedOutput(),
                testCase.getDisplayOrder()
        );
    }

    /**
     * ProblemPublished 이벤트를 Outbox 저장용 JSON으로 직렬화합니다.
     *
     * <p>직렬화 실패 시 테스트케이스 내용이나 payload를
     * 예외 메시지에 포함하지 않습니다.</p>
     */
    private String serializeEvent(
            ProblemPublishedEvent event
    ) {
        try {
            return jsonMapper.writeValueAsString(
                    event
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "ProblemPublished event serialization failed",
                    exception
            );
        }
    }
}
