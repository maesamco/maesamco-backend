# 매삼코 (Maesamco) — AI 기반 Java 마이크로러닝 플랫폼

> "매일 삼분 코딩" — Java 초보 학습자가 하루 5분, 문제 풀이 + 60초 AI 설명 + 역질문으로 이해도를 점검하는 학습 서비스

## 프로젝트 구조 (모노레포)

```
maesamco-backend/
├── docker-compose.yml
├── .env.example
├── settings.gradle / build.gradle       (버전은 루트 build.gradle의 ext 블록에서 중앙 관리)
│
├── eureka-server/       (서비스 디스커버리, 선택)
├── api-gateway/         (인증 1차 검증·라우팅·Rate Limit)
├── user-service/        (회원·인증·XP·스트릭)      :9000
├── content-service/     (커리큘럼·문제·일일퀴즈)    :9001
├── judge-service/       (코드 제출·Judge0 채점)     :9002
├── coaching-service/    (힌트·설명·AI 피드백)       :9003
│
└── infra/
    ├── diagrams/        (ERD, 인프라 설계도 등 원본)
    ├── prometheus/
    ├── grafana/
    └── loki-tempo/      (MVP 이후)
```

각 서비스는 `src/main/java/com/maesamco/{service}/global/`에 공통 예외처리·응답포맷·보안 필터의 **자기 복사본**을 갖습니다(팀 컨벤션 16절 — 서비스 간 공유 Gradle 모듈을 의도적으로 두지 않음). 한 파일을 고치면 4곳에 동일하게 반영해야 합니다.

## 처음 클론했다면 (필수, 순서대로)

```bash
git clone <이 저장소 URL>
cd maesamco-backend
```

**1) JDK 21 설치 — 사람이 직접 해야 함**
Gradle Wrapper·Docker 뭘 쓰든 이것만은 자동화가 안 됩니다. IntelliJ 기준 `File > Project Structure > SDKs > Download JDK > 21 (Temurin)`. 터미널로 확인: `java -version`이 `21`을 가리켜야 합니다.

**2) 환경변수 채우기**
```bash
cp .env.example .env
# JWT_PUBLIC_KEY, DB_PASSWORD 등 최소한 로컬 기동에 필요한 값만 우선 채워도 됩니다.
```

**3) 인프라 + 서비스 기동**
```bash
docker compose up -d
docker compose ps   # 전부 Up 상태인지 확인
```

이 3단계가 끝나면 아래 접속 주소로 확인하시면 됩니다.

## 접속 주소

| 서비스 | 주소 | 계정 |
| --- | --- | --- |
| API Gateway | http://localhost:8080 | - |
| Eureka | http://localhost:8761 | - |
| Kafka-UI | http://localhost:8085 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin / admin |
| PostgreSQL (User/Content/Judge/Coaching) | localhost:5432~5435 | .env 참고 |

## 알려진 제약 (초기 커밋 시점)

- **Judge0는 docker-compose에 포함되어 있지 않습니다**(주석 처리됨) — Judge0 자체가 PostgreSQL·Redis·Server·Worker로 구성된 별도의 다중 컨테이너 스택이라, 담당자가 [공식 저장소](https://github.com/judge0/judge0)의 docker-compose를 이 파일과 merge해서 완성해야 합니다.
- 각 서비스의 `domain/`, `application/`, `infrastructure/`, `presentation/` 패키지는 아직 비어 있습니다 — `global/`(공통 예외·응답·보안)과 `Application.java`(부팅 클래스)만 있는 상태입니다. 담당자가 이슈를 만들고 각자 채워나가면 됩니다.
- `application.yml`의 HMAC 키(`internal.hmac.keys`)는 빈 값입니다 — 서비스 쌍이 정해지면 `.env`에 실제 값을 채우고 이 설정도 채워야 합니다.
- `HmacVerificationFilter`, Argon2id `PasswordEncoder`, QueryDSL `JPAQueryFactory` Bean, Grafana 알림 규칙은 클래스/의존성만 있고 아직 실제로 연결·설정되지 않았습니다 — 담당 파트에서 순차적으로 채워나갈 예정입니다.

## 다음 단계

1. `.github/CODEOWNERS`에 실제 GitHub 아이디 채우기
2. Settings > Branches에서 `main`/`develop` 보호 규칙 설정 (CI 워크플로우는 이미 `.github/workflows/`에 있음)
3. 서비스별 [Feat] 기본 구조 생성 이슈 생성 후 착수

## 관련 문서

인프라 설계도, ERD, API 명세서, 시스템 구조도 등은 `infra/diagrams/`에 원본을 보관합니다.
