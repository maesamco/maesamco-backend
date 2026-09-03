package com.maesamco.judge.domain.entity;


/**
 * 채점 시스템 자체 실패 원인 (status = FAILED 일 때만 값 존재)
 * 학생 코드가 정상 실행되어 나온 실패(COMPILE_ERROR 등)는 SubmissionResult로 분류되고,
 * 이 enum은 Judge0·Kafka·저장 단계 등 시스템 자체가 실패한 경우의 원인 분류입니다.
 * result와 failureCode는 동시에 값을 갖지 않습니다.
 */
public enum FailureCode {
    JUDGE0_RESPONSE_FAILURE,
    KAFKA_PROCESSING_FAILURE,
    RESULT_SAVE_FAILURE,
    INTERNAL_SYSTEM_ERROR
}
