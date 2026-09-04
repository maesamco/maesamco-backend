package com.maesamco.content.problem.domain.enums;

public enum ProblemStatus {

    /** AI 생성 */
    DRAFT,

    /** AI 검증 대기 */
    VALIDATING,

    /** AI 검증 실패 */
    VALIDATION_FAILED,

    /** 관리자 승인 대기 */
    REVIEW_PENDING,

    /** 관리자 거부 */
    REJECTED,

    /** 관리자 승인된 과거 버전의 문제 */
    ARCHIVED,

    /** 관리자 승인된 현재 사용 중인 문제 */
    PUBLISHED
}