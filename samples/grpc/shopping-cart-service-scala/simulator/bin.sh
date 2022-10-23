#!/bin/sh -e

BUILD_VERSION=$1
if [[ -z "$BUILD_VERSION" ]]; then
   echo "Please provide BUILD_VERSION"
   exit 1;
fi

docker build . -t simulator:v1 --platform linux/x86_64
docker tag simulator:v1 ghcr.io/ceecer1/simulator:$BUILD_VERSION
docker push ghcr.io/ceecer1/simulator:$BUILD_VERSION