package com.maesamco.coaching.application.port;

/**
 * LLM 호출을 추상화하는 포트 — 힌트·역질문·피드백 생성 Facade가 구체 벤더를 모른 채 호출한다
 * (팀 컨벤션 "포트-어댑터 구조", 벤더 교체 시 어댑터만 교체).
 */
public interface AiModelPort {

    /**
     * @throws AiModelCallException LLM 호출 실패·타임아웃 시 — Facade가 잡아서
     *                               AiCallHistory 실패 기록 후 AI_GENERATION_FAILED로 변환한다.
     */
    AiModelResponse generate(String systemPrompt, String userPrompt);
}
