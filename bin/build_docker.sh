#!/usr/bin/env bash

mvn -f ../pom.xml -DskipTests=true spring-javaformat:apply clean package spring-boot:build-image