#!/usr/bin/env bash 

docker run \
	-e LANG="en_US.UTF-8" \
	-e BLOG_RESET_ON_REBUILD=true \
 	-e LANG=en docker.io/library/api:0.0.1-SNAPSHOT

