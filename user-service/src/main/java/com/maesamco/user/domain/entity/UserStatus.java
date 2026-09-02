package com.maesamco.user.domain.entity;

/**
 * 사용자 계정의 현재 이용 상태입니다.
 *
 * <p>회원 탈퇴는 별도의 상태값을 사용하지 않고
 * {@code BaseEntity.deletedAt}을 이용한 논리 삭제로 관리합니다.</p>
 */
public enum UserStatus {

    /**
     * 정상적으로 서비스를 이용할 수 있는 상태입니다.
     */
    ACTIVE,

    /**
     * 관리자에 의해 서비스 이용이 제한된 상태입니다.
     */
    SUSPENDED
}