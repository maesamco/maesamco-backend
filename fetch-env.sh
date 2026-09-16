#!/bin/bash
# Parameter Store(/maesamco/*)에서 값을 읽어 .env 파일을 생성한다.
# EC2 인스턴스 안에서만 실행할 것 — 결과로 나온 .env는 절대 git에 커밋하지 않는다.
#
# .env.example과 반드시 항목을 맞춰야 한다 — 새 기능이 추가돼 .env.example에
# 변수가 늘어나면 이 스크립트도 같이 갱신할 것(이번에 MAIL_*, ENCRYPTION_KEY,
# EMAIL_VERIFICATION_HMAC_KEY, DAILY_QUIZ_BATCH_* 누락을 뒤늦게 발견함).
set -e

REGION="ap-northeast-2"
PREFIX="/maesamco"

get_param() {
    aws ssm get-parameter --name "$PREFIX/$1" --with-decryption --region "$REGION" \
        --query "Parameter.Value" --output text
}

echo "# 자동 생성됨 — fetch-env.sh 실행 결과, 수정하지 말 것" > .env
echo "" >> .env

# ===== DB =====
echo "DB_USERNAME=maesamco" >> .env
echo "DB_PASSWORD=$(get_param DB_PASSWORD)" >> .env

# ===== JWT =====
echo "JWT_PUBLIC_KEY=$(get_param JWT_PUBLIC_KEY)" >> .env
echo "JWT_PRIVATE_KEY=$(get_param JWT_PRIVATE_KEY)" >> .env
echo "JWT_REFRESH_PUBLIC_KEY=$(get_param JWT_REFRESH_PUBLIC_KEY)" >> .env
echo "JWT_REFRESH_PRIVATE_KEY=$(get_param JWT_REFRESH_PRIVATE_KEY)" >> .env

# ===== 개인정보/이메일 암호화 =====
echo "ENCRYPTION_KEY=$(get_param ENCRYPTION_KEY)" >> .env
echo "EMAIL_ENCRYPTION_KEY=$(get_param EMAIL_ENCRYPTION_KEY)" >> .env
echo "EMAIL_LOOKUP_HMAC_KEY=$(get_param EMAIL_LOOKUP_HMAC_KEY)" >> .env
echo "EMAIL_VERIFICATION_HMAC_KEY=$(get_param EMAIL_VERIFICATION_HMAC_KEY)" >> .env

# ===== SMTP (이메일 인증 메일 발송) =====
# ⚠️ 실제 SMTP 계정이 필요하다 — Gmail 앱 비밀번호든 AWS SES든, 사전에
# Parameter Store에 /maesamco/MAIL_USERNAME, /maesamco/MAIL_PASSWORD를
# 채워둬야 이 스크립트가 정상 동작한다.
echo "MAIL_HOST=$(get_param MAIL_HOST)" >> .env
echo "MAIL_PORT=$(get_param MAIL_PORT)" >> .env
echo "MAIL_USERNAME=$(get_param MAIL_USERNAME)" >> .env
echo "MAIL_PASSWORD=$(get_param MAIL_PASSWORD)" >> .env
echo "MAIL_SMTP_AUTH=true" >> .env
echo "MAIL_STARTTLS_ENABLE=true" >> .env
echo "MAIL_STARTTLS_REQUIRED=true" >> .env

# ===== 서비스 간 HMAC =====
echo "HMAC_KEY_CONTENT_TO_JUDGE=$(get_param HMAC_KEY_CONTENT_TO_JUDGE)" >> .env
echo "HMAC_KEY_COACHING_TO_JUDGE=$(get_param HMAC_KEY_COACHING_TO_JUDGE)" >> .env
echo "HMAC_KEY_COACHING_TO_CONTENT=$(get_param HMAC_KEY_COACHING_TO_CONTENT)" >> .env
echo "HMAC_KEY_CONTENT_TO_COACHING=$(get_param HMAC_KEY_CONTENT_TO_COACHING)" >> .env
echo "HMAC_KEY_CONTENT_TO_USER=$(get_param HMAC_KEY_CONTENT_TO_USER)" >> .env
echo "HMAC_KEY_JUDGE_TO_USER=$(get_param HMAC_KEY_JUDGE_TO_USER)" >> .env
echo "HMAC_KEY_COACHING_TO_USER=$(get_param HMAC_KEY_COACHING_TO_USER)" >> .env

# ===== Daily Quiz 배치 (비밀값 아님, 운영 설정값 — 필요시 조정) =====
echo 'DAILY_QUIZ_BATCH_CRON="0 0 3 * * *"' >> .env
echo "DAILY_QUIZ_BATCH_ZONE=Asia/Seoul" >> .env
echo "DAILY_QUIZ_BATCH_CHUNK_SIZE=100" >> .env

# ===== LLM API =====
echo "ANTHROPIC_API_KEY=$(get_param ANTHROPIC_API_KEY)" >> .env
echo "AI_MODEL_CHAT=" >> .env
echo "GEMINI_API_KEY=" >> .env

# ===== 인프라 (비밀값 아님, docker-compose.prod.yml 내부망 주소) =====
echo "KAFKA_BOOTSTRAP_SERVERS=kafka:9092" >> .env
echo "JUDGE0_BASE_URL=http://judge0:2358" >> .env
echo "EUREKA_URL=http://eureka-server:8761/eureka/" >> .env

# ===== Grafana Alerting =====
echo "SLACK_WEBHOOK_URL=$(get_param SLACK_WEBHOOK_URL)" >> .env

# ===== Rate Limit =====
echo "RATE_LIMIT_SUBMISSIONS_PER_MIN=" >> .env
echo "RATE_LIMIT_TRUSTED_PROXY_IPS=" >> .env

# ⚠️ RDS 엔드포인트는 본인 환경에 맞게 직접 채워넣을 것 (RDS 콘솔에서 확인)
echo "RDS_ENDPOINT=REPLACE_ME.xxxxxxxxxx.ap-northeast-2.rds.amazonaws.com" >> .env

echo "완료 — .env 생성됨. RDS_ENDPOINT 값만 실제 엔드포인트로 수정해주세요."