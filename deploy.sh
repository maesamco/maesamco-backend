#!/bin/bash
# EC2 인스턴스 안에서 실행. develop 최신화 -> .env 갱신 -> 재빌드 -> 재기동.
set -e

echo "===== 1. 최신 코드 받기 ====="
git fetch origin
git checkout develop
git reset --hard origin/develop

echo "===== 2. Parameter Store에서 .env 갱신 ====="
./fetch-env.sh
# ⚠️ 최초 1회는 fetch-env.sh 실행 후 .env 파일의 RDS_ENDPOINT 값을
# 실제 엔드포인트로 직접 수정해야 함(스크립트가 자동으로 못 채움)

echo "===== 3. docker-compose.prod.yml로 재빌드 및 재기동 ====="
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d

echo "===== 4. 상태 확인 ====="
sleep 5
docker compose -f docker-compose.prod.yml ps -a

echo "===== 완료 ====="