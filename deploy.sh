#!/bin/bash
# EC2 인스턴스 안에서 실행. develop 최신화 -> .env 갱신 -> 재빌드 -> 재기동.
#
# ⚠️ 배포 스크립트 리뷰 반영:
# - build를 down보다 먼저 실행한다 — build 실패 시에도 기존 정상 서비스가
#   내려간 채로 남지 않도록 한다.
# - 마지막에 exited/unhealthy 컨테이너가 있으면 exit 1로 실패를 명확히 알린다
#   (예전엔 ps -a로 보여주기만 하고 스크립트 자체는 항상 성공으로 끝났음).
set -e

echo "===== 1. 최신 코드 받기 ====="
git fetch origin
git checkout develop
git reset --hard origin/develop

echo "===== 2. Parameter Store에서 .env 갱신 ====="
# fetch-env.sh 실행 권한이 git에서 안 살아있을 수 있어 bash로 직접 실행
bash ./fetch-env.sh

echo "===== 3. docker-compose.prod.yml 빌드 (기존 서비스는 아직 살아있는 상태) ====="
# ⚠️ 실제 배포로 발견 — 6개 서비스를 한 번에 빌드(build 인자 없이)하면
# t3.medium/large에서도 메모리 부족으로 인스턴스 자체가 응답 불능 상태에
# 빠질 수 있었다(재부팅해야 했음). 서비스 하나씩 순차적으로 빌드해서
# 동시 메모리 사용량을 줄인다 — 시간은 조금 더 걸리지만 훨씬 안정적이다.
for svc in eureka-server api-gateway user-service content-service judge-service coaching-service; do
    echo "  --- $svc 빌드 중 ---"
    docker compose -f docker-compose.prod.yml build "$svc"
done

echo "===== 4. 재기동 ====="
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml up -d

echo "===== 5. 상태 확인 ====="
sleep 10
docker compose -f docker-compose.prod.yml ps -a

# exited 상태이거나, running인데 헬스체크가 unhealthy인 컨테이너가 있으면 배포 실패로 간주
# ⚠️ 리뷰로 발견 — 예전엔 "State":"exited"만 확인해서, running인데 unhealthy인
# 컨테이너(예: healthcheck는 있지만 계속 실패 중인 상태)는 실패로 안 잡혔다.
STATUS_JSON=$(docker compose -f docker-compose.prod.yml ps -a --format json)
if echo "$STATUS_JSON" | grep -q '"State":"exited"' || echo "$STATUS_JSON" | grep -q '"Health":"unhealthy"'; then
    echo "❌ 배포 실패 — exited 또는 unhealthy 상태인 컨테이너가 있습니다. 로그를 확인하세요."
    exit 1
fi

echo "===== 완료 ====="