# Native rebuilding and relinking materials

The application uses LGPL-2.1-or-later libchewing 0.12.0, statically linked into our JNI shared library. This accompanying archive contains the exact upstream core and dictionary source archives, dependency sources, Cargo.lock in the core archive, licenses, Rust standard library source, the compiled core archives, and the JNI object files for both supported ABIs. There are no private signing keys.

For a replacement core, unpack the pinned sources, apply your changes, use Rust 1.88.0 and Android NDK 28.2.13676358, and rebuild chewing_capi for aarch64-linux-android and x86_64-linux-android. Use the cargo source directory/config for offline builds. Replace the respective objects/ABI/libchewing_capi.a and run `ANDROID_NDK_HOME=/path/to/ndk bash relink.sh arm64-v8a` (or x86_64) from this archive's root. The supplied JNI object is intentionally relinkable. Our complete Kotlin/JNI application source is also supplied in the source archive accompanying the APK.

To use a relinked library, replace lib/ABI/libzhuoran_chewing.so in the APK, align with `zipalign -P 16 -f 4`, then sign with your own Android debug key. Uninstall the original first if certificates differ. No integrity check prevents a user-modified library. Source pins and hashes are documented in third_party/chewing.lock.json. No GPL Android wrapper code is included.
