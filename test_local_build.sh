#!/usr/bin/env bash
# builds the container image the way the github actions workflow does, into the local docker
# daemon instead of publishing it.
set -euo pipefail
export IMAGE_NAME=joshlong-api
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
export ROOT_DIR
"$ROOT_DIR/.github/workflows/build-container.sh"
