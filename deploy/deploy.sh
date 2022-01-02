#!/usr/bin/env bash
set -e
set -o pipefail

APP_NAME=joshlong-com-api
IMAGE_NAME=gcr.io/${PROJECT_ID}/${APP_NAME}
docker rmi -f $IMAGE_NAME
cd $ROOT_DIR
./mvnw -DskipTests=true spring-javaformat:apply clean package spring-boot:build-image -Dspring-boot.build-image.imageName=$IMAGE_NAME
docker push $IMAGE_NAME
export RESERVED_IP_NAME=${APP_NAME}-ip
gcloud compute addresses list --format json | jq '.[].name' -r | grep $RESERVED_IP_NAME || gcloud compute addresses create $RESERVED_IP_NAME --global
kubectl apply -f $ROOT_DIR/deploy/k8s

