package com.maesamco.content.problem.infrastructure.messaging.event;

import com.maesamco.content.problem.domain.entity.ProblemVersion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 문제가 최종 발행되었음을 Judge Service에 전달하는 Kafka 이벤트입니다.
 *
 * <p>관리자의 발행 승인으로 ProblemVersion이 확정된 이후 생성되며,
 * Outbox에 저장된 뒤 Kafka로 발행됩니다.</p>
 *
 * <p>테스트케이스의 input과 expectedOutput은 채점에 필요한 민감 데이터이므로
 * 애플리케이션 로그, 예외 메시지, 외부 API 응답 등에 노출하지 않습니다.</p>
 *
 * @param eventId 이벤트 고유 식별자
 * @param eventType 이벤트 유형
 * @param eventVersion 이벤트 스키마 버전
 * @param occurredAt 이벤트 발생 시각
 * @param problemId 문제 식별자
 * @param problemVersionId 발행된 문제 버전 식별자
 * @param versionNo 문제 버전 번호
 * @param language 채점 대상 프로그래밍 언어
 * @param starterCode 문제 시작 코드
 * @param testCases 채점에 사용할 테스트케이스 목록
 * @param timeLimit 실행 시간 제한(ms)
 * @param memoryLimit 메모리 제한(MB)
 * @param publishedAt 문제 버전 발행 시각
 */
public record ProblemPublishedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID problemId,
        UUID problemVersionId,
        int versionNo,
        String language,
        String starterCode,
        List<TestCaseItem> testCases,
        int timeLimit,
        int memoryLimit,
        Instant publishedAt
) {

    public static final String EVENT_TYPE =
            "PROBLEM_PUBLISHED";

    public static final int EVENT_VERSION = 1;

    private static final int MILLIS_PER_SECOND = 1000;

    /**
     * 외부에서 전달받은 가변 List가 이벤트 생성 이후 변경되지 않도록
     * 불변 복사본으로 보관합니다.
     */
    public ProblemPublishedEvent {
        testCases = List.copyOf(testCases);
    }

    /**
     * 확정된 ProblemVersion을 기반으로 ProblemPublished 이벤트를 생성합니다.
     *
     * <p>Content Service의 실행 시간 제한은 초 단위로 저장되므로
     * Judge Service에 전달할 때 밀리초 단위로 변환합니다.</p>
     *
     * @param eventId 이벤트 고유 식별자
     * @param occurredAt 이벤트 발생 시각
     * @param problemVersionId 저장된 ProblemVersion 식별자
     * @param problemVersion 확정된 문제 버전
     * @return ProblemPublished 이벤트
     */
    public static ProblemPublishedEvent fromPublishedVersion(
            UUID eventId,
            Instant occurredAt,
            UUID problemVersionId,
            ProblemVersion problemVersion
    ) {
        ProblemVersion.ProblemVersionSnapshot snapshot =
                problemVersion.getContentSnapshot();

        List<TestCaseItem> eventTestCases =
                snapshot.testCases()
                        .stream()
                        .map(
                                testCase ->
                                        new TestCaseItem(
                                                testCase.testCaseId(),
                                                testCase.isPublic(),
                                                testCase.input(),
                                                testCase.expectedOutput(),
                                                testCase.displayOrder()
                                        )
                        )
                        .toList();

        int timeLimitMs =
                Math.multiplyExact(
                        snapshot.runningTimeLimit(),
                        MILLIS_PER_SECOND
                );

        return new ProblemPublishedEvent(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                occurredAt,
                problemVersion.getProblemId(),
                problemVersionId,
                problemVersion.getVersionNo(),
                snapshot.language().name(),
                snapshot.starterCode(),
                eventTestCases,
                timeLimitMs,
                snapshot.runningMemoryLimit(),
                problemVersion.getPublishedAt()
        );
    }

    /**
     * 발행된 문제 버전에 포함되는 테스트케이스입니다.
     *
     * @param testCaseId 테스트케이스 식별자
     * @param isPublic 공개 테스트케이스 여부
     * @param input 테스트 입력값
     * @param expectedOutput 기대 출력값
     * @param displayOrder 테스트케이스 표시 및 실행 순서
     */
    public record TestCaseItem(
            UUID testCaseId,
            boolean isPublic,
            String input,
            String expectedOutput,
            int displayOrder
    ) {
    }
}
