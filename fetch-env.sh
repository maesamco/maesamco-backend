#!/bin/bash
# Parameter Store(/maesamco/*)에서 값을 읽어 .env, judge0.conf 파일을 생성한다.
# EC2 인스턴스 안에서만 실행할 것 — 결과로 나온 .env/judge0.conf는 절대 git에
# 커밋하지 않는다(.gitignore에 이미 포함되어 있어야 함).
#
# .env.example과 반드시 항목을 맞춰야 한다 — 새 기능이 추가돼 .env.example에
# 변수가 늘어나면 이 스크립트도 같이 갱신할 것.
#
# ⚠️ 배포 스크립트 리뷰 반영:
# - RDS_ENDPOINT도 Parameter Store에서 관리한다(예전엔 REPLACE_ME placeholder를
#   매번 다시 썼는데, deploy.sh가 재배포마다 이 스크립트를 다시 실행하면서
#   실제 값이 매번 초기화되던 심각한 버그였음).
# - 임시 파일에 전부 쓴 뒤 마지막에 .env로 교체한다 — 중간에 SSM 조회가
#   실패해도 기존 .env가 반쪽짜리로 깨지지 않는다.
# - get_param이 값을 못 가져오면(빈 문자열/에러) 즉시 스크립트를 중단한다
#   (VAR=$(cmd) 형태는 set -e만으로는 실패가 항상 감지되지 않을 수 있어
#   명시적으로 검사한다).
# - judge0.conf도 이 스크립트에서 같이 생성한다(judge0.conf.template의
#   플레이스홀더 2곳을 Parameter Store 값으로 치환).
# - AI_MODEL_CHAT/GEMINI_API_KEY를 Parameter Store에서 실제로 읽어오도록 수정
#   (예전엔 항상 빈 값으로 고정되어 있어서, Gemini로 전환해도 반영이 안 됐음).
set -e

REGION="ap-northeast-2"
PREFIX="/maesamco"
TMP_ENV=$(mktemp)

# 실패하면 값을 못 가져온 것 — 즉시 중단하고 빈 값이 .env에 들어가는 것을 막는다.
get_param() {
    local value
    if ! value=$(aws ssm get-parameter --name "$PREFIX/$1" --with-decryption --region "$REGION" \
        --query "Parameter.Value" --output text 2>&1); then
        echo "❌ Parameter Store 조회 실패: $PREFIX/$1" >&2
        echo "   $value" >&2
        rm -f "$TMP_ENV"
        exit 1
    fi
    echo "$value"
}

write() {
    # ⚠️ 리뷰로 발견 — echo "$1=$(get_param "$1")" 형태는 get_param 내부의
    # exit 1이 명령어 치환(subshell)만 종료시키고, 바깥 echo 자체의 종료 상태는
    # 항상 0이라 set -e가 못 잡는다. 값을 먼저 변수에 할당해서(대입문 자체의
    # 종료 상태로 실패가 정확히 전파됨) set -e가 실제로 작동하게 한다.
    local value
    value=$(get_param "$1")
    echo "$1=$value" >> "$TMP_ENV"
}

echo "===== .env 생성 ====="
echo "# 자동 생성됨 — fetch-env.sh 실행 결과, 수정하지 말 것" > "$TMP_ENV"
echo "" >> "$TMP_ENV"

# ===== DB =====
echo "DB_USERNAME=maesamco" >> "$TMP_ENV"
write DB_PASSWORD
write RDS_ENDPOINT

# ===== JWT =====
write JWT_PUBLIC_KEY
write JWT_PRIVATE_KEY
write JWT_REFRESH_PUBLIC_KEY
write JWT_REFRESH_PRIVATE_KEY

# ===== 개인정보/이메일 암호화 =====
write ENCRYPTION_KEY
write EMAIL_ENCRYPTION_KEY
write EMAIL_LOOKUP_HMAC_KEY
write EMAIL_VERIFICATION_HMAC_KEY

# ===== Google OAuth =====
write GOOGLE_OAUTH_CLIENT_ID

# ===== SMTP =====
write MAIL_HOST
write MAIL_PORT
write MAIL_USERNAME
write MAIL_PASSWORD
write MAIL_SMTP_AUTH
write MAIL_STARTTLS_ENABLE
write MAIL_STARTTLS_REQUIRED

# ===== 서비스 간 HMAC =====
write HMAC_KEY_CONTENT_TO_JUDGE
write HMAC_KEY_COACHING_TO_JUDGE
write HMAC_KEY_COACHING_TO_CONTENT
write HMAC_KEY_CONTENT_TO_COACHING
write HMAC_KEY_CONTENT_TO_USER
write HMAC_KEY_JUDGE_TO_USER
write HMAC_KEY_COACHING_TO_USER
# ⚠️ 리뷰로 발견 — .env.example/User Service 설정엔 있는데 이 스크립트엔
# 빠져있었다(User → Content 내부 통신용).
write HMAC_KEY_USER_TO_CONTENT

# ===== Daily Quiz 배치 (비밀값 아님, 운영 설정값) =====
echo 'DAILY_QUIZ_BATCH_CRON="0 0 3 * * *"' >> "$TMP_ENV"
echo "DAILY_QUIZ_BATCH_ZONE=Asia/Seoul" >> "$TMP_ENV"
echo "DAILY_QUIZ_BATCH_CHUNK_SIZE=100" >> "$TMP_ENV"

# ===== LLM API =====
# ⚠️ 팀 결정으로 Gemini를 쓰기로 함 — AI_MODEL_CHAT/GEMINI_API_KEY를 실제로
# Parameter Store에서 읽어오도록 수정(예전엔 항상 빈 값으로 고정되어 있어서
# Gemini로 바꿔도 반영이 안 되는 버그가 있었음).
write ANTHROPIC_API_KEY
write AI_MODEL_CHAT
write GEMINI_API_KEY

# ===== 인프라 (비밀값 아님, docker-compose.prod.yml 내부망 주소) =====
echo "KAFKA_BOOTSTRAP_SERVERS=kafka:9092" >> "$TMP_ENV"
echo "JUDGE0_BASE_URL=http://judge0:2358" >> "$TMP_ENV"
echo "EUREKA_URL=http://eureka-server:8761/eureka/" >> "$TMP_ENV"

# ===== Grafana Alerting =====
write SLACK_WEBHOOK_URL
GRAFANA_ROOT_URL_VALUE=$(get_param GRAFANA_ROOT_URL)
echo "GRAFANA_ROOT_URL=$GRAFANA_ROOT_URL_VALUE" >> "$TMP_ENV"

# ===== 이슈 #310 — Caddy용 nip.io 도메인 =====
# GRAFANA_ROOT_URL(예: http://54.180.36.179:4000)에서 IP만 뽑아내
# nip.io 서브도메인 형식(점 -> 하이픈)으로 변환한다. 새 Parameter Store
# 값을 따로 추가하지 않고 기존 값에서 파생시켜, 배포자가 IP를 두 군데
# 따로 관리하다 서로 어긋나는 실수를 원천 차단한다.
EC2_IP=$(echo "$GRAFANA_ROOT_URL_VALUE" | sed -E 's#https?://##; s#:.*##')
if [ -z "$EC2_IP" ]; then
    echo "❌ GRAFANA_ROOT_URL에서 IP를 추출하지 못했습니다: $GRAFANA_ROOT_URL_VALUE" >&2
    rm -f "$TMP_ENV"
    exit 1
fi
EC2_IP_DASHED=$(echo "$EC2_IP" | tr '.' '-')
echo "EC2_ELASTIC_IP_DASHED=$EC2_IP_DASHED" >> "$TMP_ENV"

# ===== Rate Limit =====
echo "RATE_LIMIT_SUBMISSIONS_PER_MIN=" >> "$TMP_ENV"
# ⚠️ 리뷰 반영(P1) — 예전엔 빈 값으로 썼다가 배포자가 수동으로 채워 넣는
# 방식이었는데, 이 스크립트가 재배포마다 .env를 통째로 새로 만들기 때문에
# 수동으로 채운 값이 다음 배포에서 그대로 사라지는 문제가 있었다(실제로
# 재현·확인됨). docker-compose.prod.yml에서 caddy에 고정 IP(172.28.0.10)를
# 부여했으므로, 그 값과 정확히 일치하는 값을 매번 자동으로 쓴다 — CIDR이
# 아니라 정확한 IP 하나인 이유는 RateLimitFilter가 정확 일치(contains)만
# 지원하기 때문(CIDR 지원은 별도 이슈로 분리, 게이트웨이 코드 변경 필요).
# docker-compose.prod.yml의 caddy ipv4_address를 바꾸면 이 값도 반드시 같이 바꿀 것.
echo "RATE_LIMIT_TRUSTED_PROXY_IPS=172.28.0.10" >> "$TMP_ENV"

# ===== 이슈 #310 리뷰 반영(P2) — Refresh Token 쿠키 SameSite/CORS =====
# 예전엔 이 두 값을 이 스크립트가 전혀 관리하지 않아서, docker-compose.prod.yml의
# 기본값(AUTH_COOKIE_SAME_SITE=Lax, CORS_ALLOWED_ORIGINS=http://localhost:3000)이
# 매 배포마다 그대로 유지됐다. 프론트가 API와 다른 site(예: localhost:3000에서
# https://<ip>.nip.io 호출)에서 부르는 크로스 사이트 구조라면, Lax 쿠키는
# fetch/XHR 기반 크로스 사이트 POST(/auth/refresh 등)에는 전송되지 않아
# #310이 SameSite 때문에 다시 재현된다(Secure는 이제 만족하지만 SameSite에서 막힘).
#
# ⚠️ 이 두 값은 자주 바뀔 수 있어(프론트 개발 서버 포트, 배포 도메인 등)
# Parameter Store에서 관리한다. 최초 1회, 아래 값을 실제 프론트 상황에
# 맞게 등록해야 한다(예시는 로컬 개발 프론트가 크로스 사이트로 호출하는
# 현재 상황 기준):
#   aws ssm put-parameter --name /maesamco/AUTH_COOKIE_SAME_SITE --value "None" --type String
#   aws ssm put-parameter --name /maesamco/CORS_ALLOWED_ORIGINS --value "http://localhost:3000" --type String
write AUTH_COOKIE_SAME_SITE
write CORS_ALLOWED_ORIGINS

# 전부 성공했을 때만 실제 .env로 교체 — 중간 실패 시 기존 정상 .env를 보존한다.
mv "$TMP_ENV" .env
echo "완료 — .env 생성됨 (RDS_ENDPOINT 포함, Parameter Store 기준)."

echo ""
echo "===== judge0.conf 생성 ====="
# judge0.conf.template(저장소에 커밋된 템플릿)의 두 플레이스홀더를
# Parameter Store 값으로 치환해 실제 judge0.conf를 만든다.
# ⚠️ Judge0 자체 전용 DB/Redis 비밀번호다 — 우리 앱의 DB_PASSWORD와는 무관.
JUDGE0_POSTGRES_PASSWORD=$(get_param JUDGE0_POSTGRES_PASSWORD)
JUDGE0_REDIS_PASSWORD=$(get_param JUDGE0_REDIS_PASSWORD)

if [ ! -f judge0.conf.template ]; then
    echo "❌ judge0.conf.template 파일이 없습니다 — 저장소 최상위에 있어야 합니다." >&2
    exit 1
fi

sed -e "s|__JUDGE0_POSTGRES_PASSWORD__|${JUDGE0_POSTGRES_PASSWORD}|" \
    -e "s|__JUDGE0_REDIS_PASSWORD__|${JUDGE0_REDIS_PASSWORD}|" \
    judge0.conf.template > judge0.conf

echo "완료 — judge0.conf 생성됨."
