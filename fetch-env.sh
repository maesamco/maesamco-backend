#!/bin/bash
# Parameter Store(/maesamco/*)에서 값을 읽어 .env 파일을 생성한다.
# EC2 인스턴스 안에서만 실행할 것 — 결과로 나온 .env는 절대 git에 커밋하지 않는다.
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
    echo "$1=$(get_param "$1")" >> "$TMP_ENV"
}

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

# ===== SMTP =====
write MAIL_HOST
write MAIL_PORT
write MAIL_USERNAME
write MAIL_PASSWORD
echo "MAIL_SMTP_AUTH=true" >> "$TMP_ENV"
echo "MAIL_STARTTLS_ENABLE=true" >> "$TMP_ENV"
echo "MAIL_STARTTLS_REQUIRED=true" >> "$TMP_ENV"

# ===== 서비스 간 HMAC =====
write HMAC_KEY_CONTENT_TO_JUDGE
write HMAC_KEY_COACHING_TO_JUDGE
write HMAC_KEY_COACHING_TO_CONTENT
write HMAC_KEY_CONTENT_TO_COACHING
write HMAC_KEY_CONTENT_TO_USER
write HMAC_KEY_JUDGE_TO_USER
write HMAC_KEY_COACHING_TO_USER

# ===== Daily Quiz 배치 (비밀값 아님, 운영 설정값) =====
echo 'DAILY_QUIZ_BATCH_CRON="0 0 3 * * *"' >> "$TMP_ENV"
echo "DAILY_QUIZ_BATCH_ZONE=Asia/Seoul" >> "$TMP_ENV"
echo "DAILY_QUIZ_BATCH_CHUNK_SIZE=100" >> "$TMP_ENV"

# ===== LLM API =====
write ANTHROPIC_API_KEY
echo "AI_MODEL_CHAT=" >> "$TMP_ENV"
echo "GEMINI_API_KEY=" >> "$TMP_ENV"

# ===== 인프라 (비밀값 아님, docker-compose.prod.yml 내부망 주소) =====
echo "KAFKA_BOOTSTRAP_SERVERS=kafka:9092" >> "$TMP_ENV"
echo "JUDGE0_BASE_URL=http://judge0:2358" >> "$TMP_ENV"
echo "EUREKA_URL=http://eureka-server:8761/eureka/" >> "$TMP_ENV"

# ===== Grafana Alerting =====
write SLACK_WEBHOOK_URL

# ===== Rate Limit =====
echo "RATE_LIMIT_SUBMISSIONS_PER_MIN=" >> "$TMP_ENV"
echo "RATE_LIMIT_TRUSTED_PROXY_IPS=" >> "$TMP_ENV"

# 전부 성공했을 때만 실제 .env로 교체 — 중간 실패 시 기존 정상 .env를 보존한다.
mv "$TMP_ENV" .env
echo "완료 — .env 생성됨 (RDS_ENDPOINT 포함, Parameter Store 기준)."