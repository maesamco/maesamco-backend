#!/bin/bash
# EC2 인스턴스 안에서 실행. develop 최신화 -> .env 갱신 -> 재빌드 -> 재기동.
#
# ⚠️ 배포 스크립트 리뷰 반영:
# - build를 down보다 먼저 실행한다 — build 실패 시에도 기존 정상 서비스가
#   내려간 채로 남지 않도록 한다.
# - 마지막에 exited/unhealthy 컨테이너가 있으면 exit 1로 실패를 명확히 알린다
#   (예전엔 ps -a로 보여주기만 하고 스크립트 자체는 항상 성공으로 끝났음).
set -e

echo "===== 0-1. 오래된 빌드 캐시 정기 정리 (매번 실행) ====="
# 72시간(3일) 지난 캐시만 지워서, 최근 캐시는 남겨 빌드 속도 이점을 유지하면서도
# 캐시가 무한정 쌓이는 걸 막는다(이슈 #256 — 매번 재배포마다 캐시가 계속 쌓이는
# 구조라는 지적 반영).
docker builder prune -af --filter "until=72h"

echo "===== 0-2. 디스크 용량 확인 ====="
# ⚠️ 이슈 #256 — 정기 정리(0-1)로도 부족할 만큼 급하게 디스크가 찰 수 있어,
# 그런 경우엔 여기서 한 번 더 확실하게 전체 정리를 시도한다. 디스크가 꽉 차면
# set -e 때문에 스크립트가 중간(보통 docker build 단계)에서 조용히 중단되던
# 문제가 있었다. 이때 git은 이미 최신화된 뒤라, 겉으론 "배포된 것처럼" 보이는
# 애매한 상태로 남는다 — 그래서 빌드 시작 전에 미리 확인한다.
MIN_FREE_KB=5000000   # 5GB 미만이면 위험 신호로 간주
AVAILABLE_KB=$(df / --output=avail | tail -1 | tr -d ' ')
echo "현재 여유 공간: $((AVAILABLE_KB / 1024))MB"

if [ "$AVAILABLE_KB" -lt "$MIN_FREE_KB" ]; then
    echo "⚠️ 디스크 여유 공간이 부족합니다 — 캐시 전체 정리를 시도합니다."
    docker builder prune -af
    AVAILABLE_KB=$(df / --output=avail | tail -1 | tr -d ' ')
    echo "정리 후 여유 공간: $((AVAILABLE_KB / 1024))MB"

    if [ "$AVAILABLE_KB" -lt "$MIN_FREE_KB" ]; then
        echo "❌ 캐시 정리 후에도 디스크 공간이 부족합니다(여유: $((AVAILABLE_KB / 1024))MB, 필요: $((MIN_FREE_KB / 1024))MB)."
        echo "   docker system prune -af --volumes 등 수동 정리가 필요할 수 있습니다."
        echo "   (⚠️ --volumes는 Judge0 DB/Redis 데이터까지 지웁니다 — 신중히 사용)"
        exit 1
    fi
fi

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