package com.maesamco.judge.domain.entity;

public enum SubmissionStatus {
    PENDING,     // 채점 대기 (제출 접수 직후)
    QUEUED,      // Kafka 발행 완료, Judge Worker 수신 대기
    RUNNING,     // Judge0에서 채점 진행 중
    RETRY_WAIT,  // 일시적 시스템 오류로 자동 재시도 대기 (retryCount 증가)
    COMPLETED,   // 채점 완료 — 이때만 result에 값이 채워짐
    FAILED       // 재시도 소진 등으로 채점 처리 자체가 실패 (result는 null)
}
