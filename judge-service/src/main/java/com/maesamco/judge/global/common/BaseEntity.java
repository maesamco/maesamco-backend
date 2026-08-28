package com.maesamco.judge.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 감사(Audit) 컬럼 + 소프트 삭제 공통 상위 클래스 (팀 컨벤션 16절).
 *
 * ⚠️ 모든 테이블이 이걸 그대로 쓰지는 않는다 — 버전관리형/불변보존형/append-only/
 *    집계-상태형 테이블은 예외다. 매삼코 DB 테이블 명세에서 대상 테이블을 먼저 확인할 것.
 *
 * createdBy/updatedBy/deletedBy는 p_users에 FK를 걸지 않는다.
 * 인증된 행위자가 없는 시점(회원가입, AI 생성 등)에는 SYSTEM_ACTOR_ID를 채운다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public abstract class BaseEntity {

    /** 인증된 행위자가 없는 시점(회원가입, AI/LLM 초안 생성 등)에 채우는 고정 시스템 센티널. */
    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public void softDelete(UUID deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
