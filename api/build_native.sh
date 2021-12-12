#!/usr/bin/env bash
mvn -Pnative -DskipTests=true spring-javaformat:apply clean package && ./target/api

