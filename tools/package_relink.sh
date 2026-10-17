#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"
mkdir -p build
exec 9>build/relink.lock
flock 9
mkdir -p build/relink
cargo +1.88.0 vendor --locked --manifest-path vendor/libchewing/Cargo.toml build/relink/cargo > build/relink/cargo-config.toml
python3 -c 'from pathlib import Path;p=Path("build/relink/cargo-config.toml");p.write_text(p.read_text().replace("build/relink/cargo", "cargo"))'
cp vendor/archives/* build/relink/
cp -r third_party build/relink/
cp -r native build/relink/
cp -r app/src/main/cpp build/relink/jni-source
mkdir -p build/relink/objects
for abi in arm64-v8a x86_64; do
 if [[ "$abi" == arm64-v8a ]]; then triple=aarch64-linux-android; else triple=x86_64-linux-android; fi
 mkdir -p "build/relink/objects/$abi"
 cp "vendor/libchewing/target/$triple/release/libchewing_capi.a" "build/relink/objects/$abi/"
 find app/.cxx -path "*/$abi/*" -name 'chewing_jni.cpp.o' -exec cp '{}' "build/relink/objects/$abi/" \;
 test -s "build/relink/objects/$abi/chewing_jni.cpp.o"
done
cp tools/relink.sh build/relink/
cp docs/validation/relink-materials.md build/relink/README.md
rust_root="$(rustc +1.88.0 --print sysroot)"
tar -czf build/relink/rust-1.88.0-library-source.tar.gz -C "$rust_root/lib/rustlib/src/rust" library
tar --exclude='relink/libzhuoran_chewing-*.so' -czf build/ZhuoranIME-0.13-relink-materials.tar.gz.tmp -C build relink
gzip -t build/ZhuoranIME-0.13-relink-materials.tar.gz.tmp
mv build/ZhuoranIME-0.13-relink-materials.tar.gz.tmp build/ZhuoranIME-0.13-relink-materials.tar.gz
