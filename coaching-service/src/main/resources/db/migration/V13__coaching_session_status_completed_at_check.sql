-- 이슈 #45(PR #8 후속 정리)에서 발견, 이슈 #148 PR에 편승해서 반영.
--
-- p_coaching_sessions.status='COMPLETED'인데 completed_at IS NULL(또는 반대)인 행도
-- V1 베이스라인엔 CHECK 제약이 없어 DB 레벨에서는 저장 가능했다. CoachingSession.complete()가
-- 항상 status와 completedAt을 함께 세팅하고(코드 전체에서 이 둘을 건드리는 유일한 지점),
-- 이슈 #148에서 "완료 후 재도전 시 상태를 IN_PROGRESS로 되돌리는" 방향도 검토했으나 채택하지
-- 않아 상태 전이 규칙이 그대로 단순하게 유지됐다 — 이제 이 불변식이 앞으로도 안 바뀔 걸
-- 확인했으니 DB 레벨에서도 같이 보호한다.
ALTER TABLE coaching_schema.p_coaching_sessions
    ADD CONSTRAINT chk_coaching_sessions_status_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status != 'COMPLETED' AND completed_at IS NULL)
    );
