# Zhuoran IME v0.10 — Android 實體鍵盤注音原型

## 本次新增
- `.github/workflows/android-build.yml`：GitHub Actions 自動建置 debug APK 並提供 Artifact。
- 使用 JDK 17、Gradle 8.9、Android SDK 35。
- 修正密碼欄位判斷，避免一般數字欄位被誤判。
- 版本號 0.10.0。

## GitHub 取得 APK
1. 將本專案全部內容（包含 `.github`）推送至 GitHub 儲存庫根目錄。
2. 到 Actions → `Build Zhuoran IME APK` → `Run workflow`。
3. Build 成功後下載 `ZhuoranIME-debug-apk` Artifact，解壓得到 `app-debug.apk`。
4. 在 Android 裝置安裝，於系統輸入法設定啟用「斲然注音」。僅供測試，請勿輸入敏感資料。

## 驗證限制
- 本環境缺少 Android SDK/Gradle，**尚未實際完成 APK 編譯**；CI 第一次執行可能需要依錯誤訊息修正。
- 純 Kotlin 核心測試與 ZIP 完整性可在本機驗證。
- 尚未在三星手機實測；詞庫僅有少量測試詞，不能取代 Gboard。
- 注音合法性與連續斷詞仍不完整。

## 核心測試
```bash
kotlinc app/src/main/java/tw/zhuoran/ime/{Zhuyin,Composer,Candidates,CandidateSelection,PhysicalKeys}.kt tests/ComposerSmokeTest.kt -include-runtime -d /tmp/zhuoran-tests.jar
java -jar /tmp/zhuoran-tests.jar
```
