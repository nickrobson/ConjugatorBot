#!/usr/bin/env bash

git pull --rebase origin master

mvn clean package

source credentials.sh

java -jar target/conjugatorbot-*-with-dependencies.jar