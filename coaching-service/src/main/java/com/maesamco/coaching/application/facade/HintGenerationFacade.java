package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Hint;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.HintRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 오답 힌트 요청(코칭 서비스 API 명세 1번 API) — Judge Service Feign 호출 + LLM 호출 +
 * 여러 번의 DB 쓰기가 함께 일어나므로 Facade로 둔다(팀 컨벤션 2절).
 *
 * TODO(#62): Content Service의 GET /internal/v1/problems/{problemId}가 아직 없어서,
 * 지금은 Judge Service 조회 결과(제출 코드·실패 정보)만으로 힌트를 생성한다. 문제 지문·개념
 * 태그를 프롬프트에 포함하지 못해 힌트 품질이 떨어질 수 있고, attemptNo >= 8일 때 개념
 * 태그로 WeakConcept를 자동 기록하는 로직도 이 이슈가 풀리기 전까지는 구현할 수 없다
 * (skipAvailable 계산 자체는 Judge의 attemptNo만 있으면 되므로 이미 반영돼 있다).
 */
@Component
public class HintGenerationFacade {

    private static final int MAX_STAGE = 4;
    private static final int SKIP_THRESHOLD_ATTEMPT_NO = 8;
    private static final String PROMPT_VERSION = "hint-v1";

    private final JudgeServicePort judgeServicePort;
    private final CoachingSessionRepository coachingSessionRepository;
    private final HintRepository hintRepository;
    private final AiModelPort aiModelPort;
    private final AiCallHistoryRepository aiCallHistoryRepository;

    public HintGenerationFacade(
            JudgeServicePort judgeServicePort,
            CoachingSessionRepository coachingSessionRepository,
            HintRepository hintRepository,
            AiModelPort aiModelPort,
            AiCallHistoryRepository aiCallHistoryRepository
    ) {
        this.judgeServicePort = judgeServicePort;
        this.coachingSessionRepository = coachingSessionRepository;
        this.hintRepository = hintRepository;
        this.aiModelPort = aiModelPort;
        this.aiCallHistoryRepository = aiCallHistoryRepository;
    }

    public HintGenerationResult requestHint(UUID submissionId, UUID callerId) {
        SubmissionSnapshot submission = judgeServicePort.getSubmission(submissionId);

        // 소유권 검증을 상태 검증보다 먼저 한다 — 팀 컨벤션 12절(403이 소유권+상태를 같이
        // 담으면 리소스 존재 여부가 새어나간다).
        if (!submission.userId().equals(callerId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }
        if (!submission.isWrong()) {
            throw new BusinessException(ErrorCode.HINT_NOT_ALLOWED);
        }

        CoachingSession session = findOrCreateSession(submission);
        boolean skipAvailable = submission.attemptNo() >= SKIP_THRESHOLD_ATTEMPT_NO;

        // TODO(#62): skipAvailable == true일 때 문제의 개념 태그로 WeakConcept를 자동
        // 기록해야 한다(신규면 생성, 있으면 recordOccurrence()). Content Service에서
        // 개념 태그를 조회할 방법이 아직 없어 보류.

        List<Hint> existingHints = hintRepository.findByCoachingSessionId(session.getId());
        int maxStage = existingHints.stream().mapToInt(Hint::getStage).max().orElse(0);

        if (maxStage >= MAX_STAGE) {
            Hint lastHint = existingHints.stream()
                    .filter(h -> h.getStage() == MAX_STAGE)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("stage 4 힌트가 있어야 하는데 없습니다"));
            return new HintGenerationResult(session.getId(), lastHint, skipAvailable, false);
        }

        int nextStage = maxStage + 1;
        Hint hint = Hint.create(session.getId(), nextStage, generateHintContent(session, submission, nextStage, existingHints));
        Hint savedHint = hintRepository.save(hint);
        return new HintGenerationResult(session.getId(), savedHint, skipAvailable, true);
    }

    /**
     * 같은 문제를 재시도하는 동안엔 세션을 이어서 쓴다(2026-09-02 확정) — submission_id가
     * 아니라 (user_id, problem_id) + IN_PROGRESS로 찾는다. 이미 COMPLETED된 세션은 여기
     * 안 걸리므로, 그 문제를 다시 도전하면 새 세션이 만들어진다.
     */
    private CoachingSession findOrCreateSession(SubmissionSnapshot submission) {
        return coachingSessionRepository.findInProgressByUserIdAndProblemId(submission.userId(), submission.problemId())
                .orElseGet(() -> {
                    try {
                        return coachingSessionRepository.save(
                                CoachingSession.create(submission.submissionId(), submission.userId(), submission.problemId())
                        );
                    } catch (BusinessException e) {
                        // 동시 요청으로 다른 트랜잭션이 먼저 세션을 만들었다면 그걸 그대로 쓴다 —
                        // 힌트 요청 자체는 실패시킬 이유가 없다.
                        if (e.getErrorCode() == ErrorCode.COACHING_SESSION_ALREADY_EXISTS) {
                            return coachingSessionRepository.findInProgressByUserIdAndProblemId(submission.userId(), submission.problemId())
                                    .orElseThrow(() -> e);
                        }
                        throw e;
                    }
                });
    }

    private String generateHintContent(CoachingSession session, SubmissionSnapshot submission, int stage, List<Hint> previousHints) {
        String systemPrompt = """
                당신은 Java 초보 학습자를 돕는 코칭 도우미입니다. 정답 코드를 절대 알려주지 않고,
                사용자가 스스로 오류를 발견하도록 질문형 힌트를 제공합니다. 지금은 %d/4단계입니다.
                단계별 방향: 1단계 관련 Java 개념 확인, 2단계 오류 발생 가능 위치 안내,
                3단계 경계값·실행 흐름 질문, 4단계 수정 방향 제시(완성된 정답 코드는 제공하지 않음).
                이전 단계에서 이미 준 힌트가 있다면, 그 내용을 반복하지 말고 그 다음 단계로
                자연스럽게 이어지도록 하세요.
                """.formatted(stage);
        String userPrompt = """
                제출 코드:
                %s

                실패 정보: %s
                %s
                """.formatted(submission.code(), submission.failedTestSummary(), formatPreviousHints(previousHints));

        try {
            AiModelResponse response = aiModelPort.generate(systemPrompt, userPrompt);
            aiCallHistoryRepository.save(AiCallHistory.create(
                    session.getId(), AiCallPurpose.HINT, response.modelName(), PROMPT_VERSION,
                    "SUCCESS", null, response.tokenUsage(), null, 0
            ));
            return response.content();
        } catch (AiModelCallException e) {
            aiCallHistoryRepository.save(AiCallHistory.create(
                    session.getId(), AiCallPurpose.HINT, "unknown", PROMPT_VERSION,
                    "FAILED", null, null, e.getMessage(), 0
            ));
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    /**
     * AiModelPort는 단발성 호출이라 대화 이력을 서버에 유지하지 않는다 — 대신 이전 단계
     * 힌트 내용을 매 호출의 프롬프트에 텍스트로 포함시켜, 다음 단계가 앞 단계를 반복하거나
     * 결이 다른 방향으로 튀지 않고 자연스럽게 이어지게 한다.
     */
    private String formatPreviousHints(List<Hint> previousHints) {
        if (previousHints.isEmpty()) {
            return "";
        }
        String history = previousHints.stream()
                .sorted(Comparator.comparingInt(Hint::getStage))
                .map(h -> h.getStage() + "단계: " + h.getContent())
                .collect(Collectors.joining("\n"));
        return "\n지난 힌트:\n" + history;
    }

    public record HintGenerationResult(UUID coachingSessionId, Hint hint, boolean skipAvailable, boolean created) {
    }
}
