#!/usr/bin/env bash
# builds the api as a graalvm native image, packaged by spring boot's paketo integration. the
# github actions workflow publishes it; test_local_build.sh builds it into the local docker daemon.
set -euo pipefail

: "${ROOT_DIR:?ROOT_DIR is required}"
: "${IMAGE_NAME:?IMAGE_NAME is required}"

cd "$ROOT_DIR"
./mvnw -B -Pnative clean package spring-boot:build-image -Dspring-boot.build-image.imageName="$IMAGE_NAME"
if [ -n "${PUBLISH:-}" ]; then
  docker push "$IMAGE_NAME"
fi
