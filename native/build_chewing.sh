#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"
python3 tools/prepare_dependencies.py
cargo +1.88.0 build --manifest-path vendor/libchewing/Cargo.toml --locked --release -p chewing-cli -p chewing_capi
python3 tools/build_dictionary.py
if [[ "${1:-host}" == host ]]; then exit 0; fi
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK 28.2.13676358}"
if ! grep -q '28.2.13676358' "$ANDROID_NDK_HOME/source.properties"; then
  echo 'Wrong NDK version' >&2; exit 1
fi
rustup target add --toolchain 1.88.0 aarch64-linux-android x86_64-linux-android
ndk_bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ndk_bin/aarch64-linux-android26-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ndk_bin/x86_64-linux-android26-clang"
for target_triple in aarch64-linux-android x86_64-linux-android; do
  RUSTFLAGS='-Crelocation-model=pic' cargo +1.88.0 build --manifest-path vendor/libchewing/Cargo.toml --locked --release --target "$target_triple" -p chewing_capi
done
