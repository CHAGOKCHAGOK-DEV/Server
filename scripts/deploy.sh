#!/usr/bin/env bash
# EC2에서 실행된다. 새 이미지로 컨테이너를 교체하고, 헬스 체크가 실패하면 직전 이미지로 되돌린다.
# 사용: IMAGE=ghcr.io/<owner>/<repo>:<tag> bash deploy.sh
set -euo pipefail

: "${IMAGE:?IMAGE is required}"

APP_NAME=chagokchagok-server
ENV_FILE="$HOME/chagokchagok/.env"
HEALTH_URL=http://127.0.0.1:8080/actuator/health
HEALTH_RETRIES=40
HEALTH_INTERVAL_SECONDS=3

if [ ! -f "$ENV_FILE" ]; then
  echo "환경 변수 파일이 없습니다: $ENV_FILE" >&2
  exit 1
fi

run_container() {
  docker run -d \
    --name "$APP_NAME" \
    --restart unless-stopped \
    --env-file "$ENV_FILE" \
    -p 127.0.0.1:8080:8080 \
    "$1"
}

wait_until_healthy() {
  for _ in $(seq 1 "$HEALTH_RETRIES"); do
    if curl -fsS "$HEALTH_URL" > /dev/null; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL_SECONDS"
  done
  return 1
}

PREVIOUS_IMAGE=$(docker inspect --format '{{.Config.Image}}' "$APP_NAME" 2>/dev/null || true)

docker pull "$IMAGE"
docker rm -f "$APP_NAME" > /dev/null 2>&1 || true
run_container "$IMAGE"

if wait_until_healthy; then
  echo "배포 성공: $IMAGE"
  # 디스크가 작으므로 현재 이미지와 직전 이미지만 남긴다.
  docker images "${IMAGE%:*}" --format '{{.Repository}}:{{.Tag}}' \
    | grep -v -x -e "$IMAGE" -e "${PREVIOUS_IMAGE:-none}" \
    | xargs -r docker rmi > /dev/null 2>&1 || true
  docker image prune -f > /dev/null
  exit 0
fi

echo "헬스 체크 실패: $IMAGE" >&2
docker logs --tail 100 "$APP_NAME" >&2 || true

if [ -n "$PREVIOUS_IMAGE" ]; then
  echo "직전 이미지로 되돌립니다: $PREVIOUS_IMAGE" >&2
  docker rm -f "$APP_NAME" > /dev/null 2>&1 || true
  run_container "$PREVIOUS_IMAGE"
fi
exit 1
