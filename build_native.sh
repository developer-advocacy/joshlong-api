#!/usr/bin/env bash
mvn -U -Pnative -DskipTests=true spring-javaformat:apply clean package  &&  ./run_native.sh