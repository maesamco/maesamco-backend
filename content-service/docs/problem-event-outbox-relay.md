# Problem Event Outbox Relay — 다중 인스턴스 운영 가이드 (#160)

Content Service를 여러 인스턴스로 확장해도 `ProblemPublished` Outbox가
**동시에 중복 발행되지 않도록** Relay가 발행 직전에 행을 선점(claim)합니다.
Daily Quiz Outbox Relay(V12)와 같은 모델입니다.

## 1. 동작 방식

```
[Relay 인스턴스]                                     [PostgreSQL]
 ① claimNext (짧은 TX)
    SELECT ... WHERE (PENDING & 재시도 시각 도래)
                  OR (IN_PROGRESS & lease 만료)
    ORDER BY occurred_at, id LIMIT 1
    FOR UPDATE SKIP LOCKED              ───────────▶  다른 TX가 잠근 행은 건너뜀
    UPDATE status=IN_PROGRESS,
           claim_id=<새 UUID>, lease_until=now+lease
    COMMIT  (행 잠금 해제, 선점 상태는 커밋되어 다른 인스턴스에 보임)

 ② Kafka 발행 + ACK 대기 (TX 없음, 최대 publish-timeout-ms)

 ③ 결과 기록 (짧은 TX)
    claim_id 가 일치할 때만
      성공        → PUBLISHED
      일반 실패    → PENDING(+백오프) / 상한 도달 시 FAILED
      결과 불확실  → PENDING(+백오프)
      payload 초과 → FAILED
    claim_id 불일치(재선점됨) → 아무것도 바꾸지 않음 (fencing)
```

- **한 건씩 선점**합니다. 배치 전체를 선점하면 뒤쪽 건들의 lease가 앞 건 처리 시간만큼 줄어들어
  정상 처리 중에도 재선점(=중복 발행)될 수 있기 때문입니다. `batch-size`는 "한 번의 폴링에서 처리할 최대 건수"입니다.
- Kafka ACK를 기다리는 동안 DB 트랜잭션·행 잠금을 잡고 있지 않습니다.

### 상태 전이

```
PENDING ──claim──▶ IN_PROGRESS ──성공──▶ PUBLISHED
   ▲                  │  │
   └──실패/불확실(백오프)─┘  └──상한 도달/payload 초과──▶ FAILED
                      │
                      └──lease 만료──▶ (다른 인스턴스가 재선점)
```

DB CHECK 제약 `chk_problem_event_outboxes_claim`이
"`IN_PROGRESS` ⇔ `claim_id`, `lease_until` 존재"를 강제합니다.

## 2. 설정

| 프로퍼티 | 기본값 | 설명 |
|---|---|---|
| `outbox.problem-published.relay.enabled` | `false` | Relay 활성화 |
| `outbox.problem-published.relay.fixed-delay-ms` | `1000` | 폴링 간격 |
| `outbox.problem-published.relay.batch-size` | `50` | 폴링 1회당 최대 처리 건수 |
| `outbox.problem-published.relay.publish-timeout-ms` | `5000` | Kafka ACK 대기 시간 |
| `outbox.problem-published.relay.lease-duration-ms` | `60000` | **신규.** 선점 유지 시간 |

`lease-duration-ms`는 `publish-timeout-ms + 1000ms` 이상이어야 하며, 아니면 **애플리케이션 기동이 실패**합니다.

lease 튜닝 기준:

- 너무 짧으면: 정상 처리 중인 행(GC 정지, DB 지연 등)을 다른 인스턴스가 재선점 → 중복 발행 가능성 증가
- 너무 길면: 인스턴스가 죽었을 때 해당 이벤트의 재발행이 그만큼 늦어짐
- 기본 60초 = ACK 대기 5초 대비 충분한 여유 + 장애 시 1분 내 복구

## 3. 보장 범위

| 항목 | 보장 |
|---|---|
| 여러 인스턴스가 같은 Outbox를 동시에 선점 | ❌ 발생하지 않음 (SKIP LOCKED + IN_PROGRESS lease) |
| 처리 중 인스턴스 종료 시 이벤트 유실 | ❌ 발생하지 않음 (lease 만료 후 재선점) |
| 전달 보장 | **At-least-once** (기존과 동일) |

중복 발행이 **완전히 0이 되는 것은 아닙니다.** 아래 경우에는 같은 `eventId`가 다시 발행될 수 있습니다.

1. Kafka 발행 성공 후 PUBLISHED 기록 전에 인스턴스가 종료됨 → lease 만료 후 재발행
2. ACK timeout 등 발행 결과가 불확실함 → PENDING으로 되돌려 재발행
3. Worker가 lease보다 오래 멈춘 뒤(긴 GC 등) 발행을 계속함 → 재선점한 Worker도 발행.
   이때 늦은 Worker의 **결과 기록**은 `claim_id` 불일치로 무시되지만, 이미 나간 Kafka 메시지는 되돌릴 수 없습니다.

따라서 **Consumer의 `eventId` 기반 멱등 처리는 반드시 유지**해야 합니다.

> 인스턴스 간 시계 차이: lease 판단은 각 인스턴스의 `Instant.now()`를 사용합니다.
> 인스턴스 시계가 lease보다 크게 어긋나면 조기 재선점이 생길 수 있으므로 NTP 동기화를 전제로 합니다.

## 4. 장애 대응 / 운영 쿼리

스키마: `content_schema.p_problem_event_outboxes`

**현재 처리 중인 행과 남은 lease**

```sql
SELECT id, event_id, claim_id, lease_until, lease_until - now() AS remaining, retry_count
FROM content_schema.p_problem_event_outboxes
WHERE status = 'IN_PROGRESS'
ORDER BY lease_until;
```

**lease가 만료됐는데 오래 남아있는 행** (Relay가 모두 꺼져있거나 폴링이 멈춘 신호)

```sql
SELECT id, event_id, lease_until
FROM content_schema.p_problem_event_outboxes
WHERE status = 'IN_PROGRESS' AND lease_until < now() - INTERVAL '5 minutes';
```

→ Relay가 켜진 인스턴스가 하나라도 있으면 다음 폴링에서 자동 재선점됩니다. 별도 조치는 필요 없고,
Relay(`PROBLEM_PUBLISHED_OUTBOX_RELAY_ENABLED`)가 활성화된 인스턴스가 있는지 먼저 확인합니다.

**즉시 재처리가 필요할 때** (lease 만료를 기다리지 않고 풀기 — 해당 인스턴스가 확실히 죽었을 때만)

```sql
UPDATE content_schema.p_problem_event_outboxes
SET status = 'PENDING', claim_id = NULL, lease_until = NULL, next_attempt_at = NULL
WHERE id = :outbox_id AND status = 'IN_PROGRESS';
```

**FAILED 이벤트 수동 재시도** (원인 해결 후)

```sql
UPDATE content_schema.p_problem_event_outboxes
SET status = 'PENDING', retry_count = 0, next_attempt_at = NULL, last_error = NULL
WHERE id = :outbox_id AND status = 'FAILED';
```

**로그 키워드**

- `lease가 만료된 IN_PROGRESS Outbox를 재선점합니다` — 이전 Worker가 죽었거나 lease보다 오래 걸림. 자주 보이면 lease를 늘리거나 원인 확인
- `현재 선점을 보유하지 않은 Worker입니다` — fencing으로 늦은 결과가 무시됨(중복 발행 가능성이 있었던 케이스)
- `재시도 상한(5) 도달 — FAILED 처리` — 수동 확인 필요

## 5. 배포 순서

V18 마이그레이션은 컬럼 추가와 CHECK 제약 교체뿐이며 기존 데이터(IN_PROGRESS 없음)는 그대로 통과합니다.
이전 버전 인스턴스는 IN_PROGRESS를 모르므로(선점 없이 PENDING만 조회), **구버전과 신버전 Relay를 섞어서 동시에 켜지 않습니다.**
롤링 배포 중에는 Relay를 한 인스턴스에서만 켜거나, 모든 인스턴스가 신버전이 된 뒤 Relay를 확장합니다.
