-- user_schema 초기 스키마 (Flyway V1 베이스라인)
-- 원본: 매삼코_ERD.sql (user_schema 섹션) — 매삼코_DB_테이블_명세.md와 100% 동기화된 검증본에서 분리
-- 서비스별 물리적으로 독립된 PostgreSQL 인스턴스를 쓰므로(팀 컨벤션 16절), 이 파일은
-- user_schema 스키마와 그 소유 테이블만 담는다 — 다른 서비스 스키마는 만들지 않는다.
-- 크로스 서비스 참조는 전부 "논리 FK"(물리 제약 없는 UUID 컬럼)로, 이 파일엔 등장하지 않는다.

CREATE SCHEMA IF NOT EXISTS user_schema;

CREATE TABLE user_schema.p_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(500) NOT NULL,
    email_lookup_hash CHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED')),
    java_experience_months INT NOT NULL DEFAULT 0 CHECK (java_experience_months >= 0),
    learning_level VARCHAR(20) NOT NULL DEFAULT 'BEGINNER' CHECK (learning_level IN ('BEGINNER','BASIC')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);
COMMENT ON TABLE user_schema.p_users IS '회원 계정·권한·학습 프로필 관리';
COMMENT ON COLUMN user_schema.p_users.email IS '로그인 이메일 암호문 — AES-256-GCM 암호화';
COMMENT ON COLUMN user_schema.p_users.email_lookup_hash IS '정규화 이메일의 HMAC-SHA256 해시 — 로그인 조회·중복 확인용';
COMMENT ON COLUMN user_schema.p_users.role IS 'ADMIN은 셀프 가입 불가, 시드 데이터/운영자 부여만';
COMMENT ON COLUMN user_schema.p_users.status IS '탈퇴는 이 컬럼이 아니라 deleted_at으로 표현';
COMMENT ON COLUMN user_schema.p_users.deleted_at IS '탈퇴 확정 시각';

CREATE TABLE user_schema.p_user_invalidation_outboxes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_user_id UUID NOT NULL,
    invalidated_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('WITHDRAWAL','SUSPENSION','PASSWORD_CHANGE','LOGOUT_ALL')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DONE')),
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_retry_at TIMESTAMPTZ,
    last_error TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    FOREIGN KEY (target_user_id) REFERENCES user_schema.p_users(id)
);
COMMENT ON TABLE user_schema.p_user_invalidation_outboxes IS '사용자 토큰 즉시 무효화 아웃박스 — Redis 반영 유실 방지';
COMMENT ON COLUMN user_schema.p_user_invalidation_outboxes.status IS 'PENDING: 릴레이 재시도 대상 / DONE: Redis 반영 완료';

CREATE TABLE user_schema.p_user_interest_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    concept_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    FOREIGN KEY (user_id) REFERENCES user_schema.p_users(id)
);
COMMENT ON TABLE user_schema.p_user_interest_concepts IS '사용자 관심 Java 개념(다대다)';
COMMENT ON COLUMN user_schema.p_user_interest_concepts.concept_id IS '논리 FK → Content Service p_concepts.id (물리 FK 없음, MSA 경계)';

CREATE TABLE user_schema.p_user_gamification_states (
    user_id UUID PRIMARY KEY,
    total_xp BIGINT NOT NULL DEFAULT 0 CHECK (total_xp >= 0),
    level INT NOT NULL DEFAULT 1 CHECK (level >= 1),
    current_streak INT NOT NULL DEFAULT 0 CHECK (current_streak >= 0),
    longest_streak INT NOT NULL DEFAULT 0 CHECK (longest_streak >= 0),
    last_activity_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id) REFERENCES user_schema.p_users(id)
);
COMMENT ON TABLE user_schema.p_user_gamification_states IS 'XP·레벨·스트릭 현재 상태(사용자 1:1)';
COMMENT ON COLUMN user_schema.p_user_gamification_states.version IS '낙관적 락(@Version) — XP·스트릭 동시 갱신 충돌 방지';

CREATE TABLE user_schema.p_xp_histories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    source_event_id UUID,
    reward_type VARCHAR(30) NOT NULL CHECK (
        reward_type IN (
            'FIRST_CORRECT',
            'COACHING_COMPLETED',
            'DAILY_GOAL_COMPLETED',
            'DAILY_QUIZ_COMPLETED',
            'STREAK_MILESTONE',
            'ADMIN_ADJUSTMENT'
        )
    ),
    source_type VARCHAR(30) NOT NULL CHECK (source_type IN ('SUBMISSION','COACHING','DAILY_ACTIVITY','DAILY_QUIZ','STREAK','SYSTEM')),
    source_id UUID,
    problem_id UUID,
    amount INT NOT NULL CHECK (amount <> 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    reward_date DATE,
    description VARCHAR(255),
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id) REFERENCES user_schema.p_users(id)
);
COMMENT ON TABLE user_schema.p_xp_histories IS 'XP 지급·차감 불변 이력';
COMMENT ON COLUMN user_schema.p_xp_histories.source_event_id IS 'Kafka 이벤트 중복 소비 방지용 — 부분 UNIQUE(IS NOT NULL)';
COMMENT ON COLUMN user_schema.p_xp_histories.amount IS '지급은 양수, 조정·회수는 음수';

CREATE TABLE user_schema.p_streak_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    milestone_days INT NOT NULL CHECK (milestone_days > 0),
    achieved_at TIMESTAMPTZ NOT NULL,
    bonus_xp INT NOT NULL DEFAULT 0 CHECK (bonus_xp >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, milestone_days),
    FOREIGN KEY (user_id) REFERENCES user_schema.p_users(id)
);
COMMENT ON TABLE user_schema.p_streak_milestones IS '스트릭 마일스톤(3/7/30일) 달성 기록';
