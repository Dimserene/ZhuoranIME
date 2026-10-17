# 斲然注音 0.13 — 新酷音核心整合測試版

Android 8.0 以上，支援 arm64-v8a 與 x86_64。離線使用隨 App 安裝的完整新酷音詞庫，停用自動學習，不需要網路權限。這是整合測試版，尚未在三星手機與日常使用的各個 App 實測。

## 使用

- 安裝後到系統「鍵盤清單及預設」啟用「斲然注音」，再切換使用。與先前 CI 測試版簽章不同，需先移除舊版才能安裝；沒有覆蓋安裝驗證。
- 虛擬鍵盤與外接 QWERTY 使用標準注音排列。聲母、介音、韻母可亂序輸入；同類注音自動替換。聲調完成音節，一聲按空白。
- 連續輸入後仍是中文組字，可移動游標並回刪。有組字時空白或「選字」開啟游標左字的候選；句首對應第一字。
- 點候選、數字 1–9／0 或 F1–F9 只更新組字，不立即提交。候選頁每頁最多十項，可上一頁／下一頁；外接 PageUp／PageDown 翻頁。
- 選字中 Enter 確認候選；一般組字中 Enter 確認整句。下一次獨立 Enter 才執行 App 的傳送／搜尋等動作。按住 Enter 不會在組字確認後接著傳送。
- Esc／取消先清未完成注音，其次關閉候選，再清除組字。⌫ 刪除目前注音或游標左字。
- 中文組字上限 32 字；超過時保留原文並提示先確認，不自動送出前半句。
- 無效注音會保留並阻止切換模式／插入標點，請修改、取消，或明確點「送出注音原文」。
- ABC 切英文，?123 切數字符號，⇧ 切英文大小寫；外接 Shift 獨立按放切中英。⌨ 開啟系統輸入法選擇。
- 密碼、數字、電話與日期欄位略過中文引擎；引擎無法載入時仍能輸入英文與切換輸入法。

## 驗證與限制

具體建置、測試與雜湊紀錄見 `docs/validation/chewing-integration.md`。少量舊 TSV 僅保留為舊回歸測試資料，正式 service 不讀取或回退該詞庫。

尚無滑行、語音、表情符號面板、長按連刪、個人詞庫管理及使用者學習。離開／重新開始欄位時會結束當前編輯器的 composing 區間並清空引擎狀態；不把未確認內容搬到下一個欄位。

## 建置

固定環境：JDK 17、Gradle 8.9、Kotlin 2.0.21、Android SDK 35、NDK 28.2.13676358、CMake 3.22.1、Rust 1.88.0。

```bash
rustup toolchain install 1.88.0 --profile minimal --component rust-src
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash native/build_chewing.sh android
bash native/build_host_jni.sh
python3 tools/package_assets.py
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
gradle :app:connectedDebugAndroidTest
python3 tools/check_apk.py app/build/outputs/apk/debug/app-debug.apk --lock third_party/chewing.lock.json --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt"
bash tools/package_relink.sh
```

來源固定於 `third_party/chewing.lock.json`。核心、詞庫及依賴授權全文隨 APK 附上；交付必須同時提供對應 App 原始碼與原生重連結材料，見 `docs/validation/relink-materials.md`。不包含第三方 GPL Android 包裝程式碼。私密簽章金鑰不得公開或放入交付包。
