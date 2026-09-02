package com.maesamco.user.domain.entity;

/**
 * 사용자가 서비스에서 가지는 권한입니다.
 */
public enum UserRole {

    /**
     * 일반 학습 사용자입니다.
     */
    USER,

    /**
     * 문제 및 사용자를 관리할 수 있는 관리자입니다.
     */
    ADMIN
}