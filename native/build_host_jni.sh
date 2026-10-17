#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"
java_root="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
mkdir -p vendor/host
g++ -std=c++17 -fPIC -shared -O2 -Wl,-z,max-page-size=16384 -I"$java_root/include" -I"$java_root/include/linux" -Ivendor/libchewing/capi/include \
 app/src/main/cpp/chewing_jni.cpp vendor/libchewing/target/release/libchewing_capi.a -o vendor/host/libzhuoran_chewing.so -ldl -lpthread -lm
