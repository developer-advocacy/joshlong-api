#!/usr/bin/env bash
mvn -DskipTests=true spring-javaformat:apply clean package && \
  java -jar target/api-0.0.1-SNAPSHOT.jar -DspringAot=true