package com.maesamco.judge.application.port;

import java.util.List;

public interface JudgeExecutionPort {

    /**
     * 테스트케이스별 실행 요청을 batch로 제출하고, 요청 순서와 동일한 순서의 토큰 목록을 반환합니다.
     * 개별 항목이 제출 자체에 실패하면 그 위치의 토큰은 null.
     */
    List<String> submitBatch(List<JudgeExecutionRequest> requests);

    /**
     * 토큰 목록으로 채점 결과를 조회합니다. 아직 안 끝난 항목은 status가 IN_QUEUE/PROCESSING으로 옵니다.
     */
    List<JudgeExecutionResult> fetchResults(List<String> tokens);
}