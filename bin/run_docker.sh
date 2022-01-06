#!/usr/bin/env bash 

docker run \
	-e LC_ALL="en_US.UTF-8" \
	-e LANG="en_US.UTF-8" \
	-e LANGUAGE="en_US.UTF-8" \
	-e LANG="en_US.UTF-8" \
	-e BLOG_RESET_ON_REBUILD=true \
	-e JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8 " \
 	docker.io/library/api:0.0.1-SNAPSHOT


