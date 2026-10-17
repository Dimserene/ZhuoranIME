# 斲然注音 0.13.0 整合驗證

驗證日期：2026-10-17。App versionCode 13；底層 libchewing 0.12.0。這是可供安裝的整合測試版，沒有三星手機／日常第三方 App 實測，也沒有實體 arm64 或 16KB 頁面裝置的執行證據。

## 實際執行結果

- 固定來源／授權證據驗證通過；11 項 Python 檢查工具測試通過，包含雜湊破壞、缺授權、未審核資料、路徑越界、缺 ABI、錯架構及 ELF 映射錯誤。
- Gradle 8.9、AGP 8.7.3、Kotlin 2.0.21、JDK17：App、兩 ABI JNI、instrumentation APK 建置成功。
- 36 項 JVM tests 通過。原有 14 項回歸案例保留；JNI 測試載入真實新酷音完整詞庫；控制器的記憶體引擎替身只作事件路由驗證。
- 真實核心測試涵蓋「你好」「我想去台北」「今天天氣很好」、明確候選保留、句中刪改、候選頁界線與無效索引、32字上限、第33字拒絕且無提交、完整1,397種允許讀音、補充平面候選「𩣺」的 UTF-8／code point 游標及刪除。
- Android API26：最終版25項 instrumentation 全數通過（63.972秒），包含32字上限後仍可確認、實體標點經控制器及Tab不攔截、大字型字形不裁切。標點回歸案例在舊APK上先失敗，於最終APK上通過。
- Android API35：23項 instrumentation 全數通過（361.293秒），觸控／物理／整合另6項重跑通過（66.593秒）。滿32字後攔截新音節與實體標點／Tab路由的最後純Kotlin修正以JVM測試及API26的真實核心整合測試另行驗證；遠端CI完整矩陣尚未執行。
- 編輯器測試採用 Android 的真實 EditText／BaseInputConnection，涵蓋組字 Enter、空白 Enter 的 SEND／NO_ENTER_ACTION、舊 session 不得寫新欄位、重新開始／外部游標移動、補充平面字元及密碼／數字政策。不是第三方 App 端對端測試。
- 觸控測試檢查橫向、1.5倍字型下按鍵正面積、控制器接受後才切模式、輸入法切換鍵 callback；觸控與物理按鍵走同一控制器。實際手機觸控手感尚未驗證。
- APK 驗證涵蓋 arm64-v8a/x86_64 的 ELF e_machine、LOAD邊界及對齊同餘、16KB LOAD與ZIP對齊、完整詞庫大小／雜湊、固定manifest／lock、所有授權文字雜湊、版本與無網路權限。
- 使用提供的 JNI object 與 core static archive，已實際重連結兩 ABI 的替代共享函式庫並通過 ELF 驗證。重連結材料內含 Cargo 依賴來源與 Rust1.88標準函式庫來源；未包含私鑰。
- 獨立程式審查發現並修正：按住Enter可能在組字提交後傳送、外部游標移動後沿用舊座標、ELF檢查不足；審查者已重查修正。最後實體鍵盤標點fallback經審查加上控制字元排除，Tab維持交由編輯器處理。

## 來源

- libchewing commit：`05ae6bcb9309c466a1b32d69c146bc583be04747`
- libchewing-data commit：`c44e81aef24b06f1509f19e1be54c99812d0c43f`
- 上游 release archive SHA-256：`6a7fae4aaa6e6ce2bd9f70f0016c553585ed5aca1e086476749a03ac5c3f4cb0`，與上游發布值一致。
- 固定資料 git archive SHA-256：`c347299a083c74d8d322a390d038bcd49345f033e1bf2008dc4211db1dd9ddaf`，由固定commit本地產生，不宣稱是上游發布的digest。
- APK 與對應來源／重連結材料的最終雜湊列於交付的 `SHA256SUMS.txt`。

## 簽章與交付限制

新 debug certificate SHA-256：`ffda7204671fd9e44127d2abacd06d861da53f373bd7d5b3e9a1474cf523b652`。
v0.12 certificate：`6d53f7c52a5f0227e9b499a141dac958055512e4ffa93cf67c0438c649949e0c`。
兩者不同，需先移除舊測試版；不宣稱可直接覆蓋更新。沒有將簽章私鑰放入公開來源或交付材料。

公開 GitHub 更新被自動核准審查擋下，要求使用者明確同意將本批程式公開至 Dimserene/ZhuoranIME。目前程式與可驗證交付材料在本地完成；尚未將本批實作更新至公開分支、執行遠端CI或建立新草稿PR，也未合併main。

## 效能觀測

API26 軟體模擬器測得一次新建引擎context（含詞庫安裝／驗證）1694 ms，連續32音節4083 ms。這是未使用KVM的模擬器基準，API35 另測得新建引擎context 8239 ms、連續32音節13111 ms。不能推估手機速度，也沒有在手機上宣稱達到任何效能門檻。

API35軟體模擬器初次測試程序曾崩潰，診斷期間系統UI、電話等系統程序亦出現多次啟動ANR；後續重跑通過，首次崩潰原因未完全確認。最終觸控字形測試已確認1.5倍字型下41個注音鍵的注音行不被裁切。
