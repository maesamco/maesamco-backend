# 매삼코 (Maesamco) — AI 기반 Java 마이크로러닝 플랫폼

> "매일 삼분 코딩" — Java 초보 학습자가 하루 5분, 문제 풀이 + 60초 AI 설명 + 역질문으로 이해도를 점검하는 학습 서비스

## 프로젝트 구조 (모노레포)

```
maesamco-backend/
├── docker-compose.yml
├── .env.example
├── settings.gradle / build.gradle / gradle/libs.versions.toml
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

## 로컬 실행

```bash
cp .env.example .env   # 값 채우기 (JWT_PUBLIC_KEY 등)

# 최초 1회: Gradle Wrapper 생성 (아래 "알려진 제약" 참고)
gradle wrapper --gradle-version 8.10

# 인프라 + 전체 서비스 기동
docker compose up -d

# 상태 확인
docker compose ps
```

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

- **Gradle Wrapper 실행파일(`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`)이 아직 없습니다.** `gradle/wrapper/gradle-wrapper.properties`만 있는 상태입니다. 로컬에 Gradle이 설치되어 있다면 저장소 루트에서 `gradle wrapper --gradle-version 8.10`을 한 번 실행해 생성한 뒤 커밋해주세요(`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` 3개 파일이 생깁니다).
- **Judge0는 docker-compose에 포함되어 있지 않습니다**(주석 처리됨) — Judge0 자체가 PostgreSQL·Redis·Server·Worker로 구성된 별도의 다중 컨테이너 스택이라, 담당자가 [공식 저장소](https://github.com/judge0/judge0)의 docker-compose를 이 파일과 merge해서 완성해야 합니다.
- 각 서비스의 `domain/`, `application/`, `infrastructure/`, `presentation/` 패키지는 아직 비어 있습니다 — `global/`(공통 예외·응답·보안)과 `Application.java`(부팅 클래스)만 있는 상태입니다. 담당자가 이슈를 만들고 각자 채워나가면 됩니다.
- `application.yml`의 HMAC 키(`internal.hmac.keys`)는 빈 값입니다 — 서비스 쌍이 정해지면 `.env`에 실제 값을 채우고 이 설정도 채워야 합니다.

## 다음 단계

1. 팀원이 각자 로컬에서 Gradle Wrapper 생성 후 커밋
2. `.github/CODEOWNERS`에 실제 GitHub 아이디 채우기
3. Settings > Branches에서 `main`/`develop` 보호 규칙 설정 (CI 워크플로우는 이미 `.github/workflows/`에 있음)
4. 서비스별 [Feat] 기본 구조 생성 이슈 생성 후 착수

## 관련 문서

인프라 설계도, ERD, API 명세서, 시스템 구조도 등은 `infra/diagrams/`에 원본을 보관합니다.
