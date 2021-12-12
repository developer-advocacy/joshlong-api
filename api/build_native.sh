#!/usr/bin/env bash
mvn spring-javaformat:apply && mvn -Pnative -DskipTests=true clean package
