package com.maesamco.judge.domain.entity;

/**
 * 제출 코드 언어 : 요청에 없으면 기본값 JAVA.
 * MVP는 Java 17(Judge0 채점 대상 언어, 팀 컨벤션 1절)만 지원 — 확장 여지로 enum 유지하는 것으로 판단했습니다.
 */
public enum SubmissionLanguage {
    JAVA
}
