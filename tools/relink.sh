#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_NDK_HOME:?Use Android NDK 28.2.13676358}"
abi="${1:?arm64-v8a or x86_64}"
case "$abi" in
 arm64-v8a) triple=aarch64-linux-android;;
 x86_64) triple=x86_64-linux-android;;
 *) exit 1;;
esac
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/${triple}26-clang++" \
 -shared -static-libstdc++ -Wl,-z,max-page-size=16384 -Wl,-soname,libzhuoran_chewing.so \
 "objects/$abi/chewing_jni.cpp.o" "objects/$abi/libchewing_capi.a" -llog -ldl -lm -o "libzhuoran_chewing-$abi.so"
