#!/usr/bin/env bash
mvn -f ../pom.xml -U -Pnative -DskipTests=true spring-javaformat:apply clean package