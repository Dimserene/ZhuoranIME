# 斲然注音新酷音核心整合 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 執行方式由使用者審閱計畫時選定；本計畫建議 Native，由同一實作者依序完成。

**Goal:** 保留斲然注音的觸控與外接鍵盤，加入可離線連續轉字、移動游標與局部改選的組字核心，並產生可驗證來源與測試結果的 Android 測試版。

**Architecture:** Kotlin 控制器統一處理按鍵狀態及 Android 提交；未完成音節只在 Kotlin 保存，已轉字組字串只由 native 核心保存。自行撰寫 JNI 橋接 libchewing 的公開 C API，不複製第三方 Android 包裝程式。所有原生存取序列化，每次操作回傳完整快照。

**Tech Stack:** Kotlin 2.0.21、AGP 8.7.3、Gradle 8.9、JDK 17、Android SDK 35、minSdk 26、NDK 28.2.13676358、Rust 1.88.0、libchewing 0.12.0 公開 C API、JUnit 4、Android instrumentation。

**Spec:** `docs/superpowers/specs/2026-10-17-chewing-engine-design.md`

基準：`work/virtual-keyboard` 的 `22526cc555b911fad2a59a270181d698c7ef5fa6`。本文件尚未執行，不代表 native 整合或新 APK 已完成。

## Global Constraints

- Android 8.0 以上；第一階段 ABI 為 `arm64-v8a` 與 `x86_64`。
- 離線繁體中文，系統詞庫隨 App 安裝，不需要網路下載。
- 保留標準注音排列、亂序組字與同類聲母／介音／韻母替換；既有英文與符號功能保留。
- 候選每頁最多十個，數字 1–9、0 與 F1–F9 的意義依輸入狀態決定。
- 有組字時 Enter 不得觸發 App 的傳送、搜尋或換行；選候選只更新組字串。
- 停用自動學習，不新增個人詞庫管理介面。
- 系統資料與使用者資料分離；不將輸入內容寫入紀錄，不交給另一個欄位。
- 固定依賴版本及雜湊；詞庫授權未確認不得納入對外 APK；核心失敗不得暗中回退小詞庫。
- 不複製 GPL Android 包裝專案；本計畫不將核心的 LGPL 標示當成所有資料與相依元件的統一授權。
- 私密簽章金鑰不得提交公開儲存庫或公開 Artifact；未配置固定金鑰時，交付須明示測試版可能需移除舊版。

## Review Focus

1. 連續快速按鍵與欄位切換交錯：舊 native 快照不可套用到新欄位。Task 5 的 `staleSessionCannotWriteToNewEditor` 驗證。
2. 長句達到核心容量：不得在 Enter 前偷偷提交前半句。Task 3 的 `capacityLimitPreservesPreeditWithoutCommit` 驗證。
3. 核心回傳的罕用字含補充平面字元：游標與刪除不可切斷 surrogate pair。Task 5 的 `supplementaryCharacterCursorAndDeletionStayAligned` 驗證。
4. App 要求重啟輸入、選取或外部移動游標：不可把整句重複插入或覆蓋無關文字。Task 5 的 `restartAndExternalSelectionDoNotDuplicatePreedit` 驗證。
5. 更新詞庫途中中斷或資料損壞：仍保留上一版本，冷啟動失敗時可輸入英數並切換輸入法。Task 2/6 的原子安裝及故障測試驗證。

---

## 檔案責任與共用介面

| 路徑（package 根為 `app/src/main/java/tw/zhuoran/ime/`） | 責任 |
| --- | --- |
| `engine/EngineTypes.kt` | 定義 Reading、EngineSnapshot、EngineReply、EngineCommand |
| `engine/ConversionEngine.kt` | 純 Kotlin 轉換介面；native 實作及測試替身都遵守相同契約 |
| `engine/NativeChewing.kt` | JNI 薄介面、原生 handle 生命週期，無 UI 判斷 |
| `engine/ChewingEngine.kt` | C API 呼叫適配、游標位置及候選頁整理、停用學習 |
| `engine/DictionaryInstaller.kt` | 雜湊驗證、版本化私有目錄及原子切換 |
| `input/SyllableBuffer.kt` | 目前一個未完成音節及輸入次序 |
| `input/InputController.kt` | 狀態轉移；只回傳輸入結果，不直接持有 Android View |
| `input/PhysicalKeyMapper.kt` | KeyEvent 到共用輸入動作；獨立 Shift 按放判定 |
| `editor/AndroidEditorBridge.kt` | InputConnection、組字區間、UTF-16 與輸入工作階段 |
| `TouchKeyboardView.kt`、`CandidateBarView.kt` | 共用動作入口與候選頁 UI |
| `ZhuoranImeService.kt` | 建立上述元件及 Android 生命週期，移出舊內嵌組字邏輯 |
| `app/src/main/cpp/chewing_jni.cpp`、`CMakeLists.txt` | 自有 JNI 與原生連結 |
| `native/`、`tools/`、`third_party/` | 固定來源、建置及依賴紀錄；不保存私鑰 |

共用型別固定如下，避免每個任務自行命名：

```kotlin
// 以下是介面決策，不是已完成的產品程式碼。
data class Reading(val zhuyin: String)
data class Candidate(val id: Int, val text: String)
data class CandidatePage(val items: List<Candidate>, val page: Int, val pages: Int, val focus: Int)
data class EngineSnapshot(val preedit: String, val cursorCodePoint: Int, val candidates: CandidatePage?)
data class EngineReply(val snapshot: EngineSnapshot, val accepted: Boolean, val committed: String = "", val error: EngineError? = null)
enum class EngineError { UNAVAILABLE, INVALID_READING, CAPACITY, BAD_STATE }
sealed interface EngineCommand {
    data class Insert(val reading: Reading) : EngineCommand
    data class Move(val delta: Int) : EngineCommand
    data object DeleteBefore : EngineCommand
    data object OpenCandidates : EngineCommand
    data class MoveCandidate(val delta: Int) : EngineCommand
    data class ChangePage(val delta: Int) : EngineCommand
    data class Choose(val indexOnPage: Int) : EngineCommand
    data object CloseCandidates : EngineCommand
    data object Commit : EngineCommand
    data object Clear : EngineCommand
}
interface ConversionEngine : AutoCloseable {
    fun dispatch(command: EngineCommand): EngineReply
    fun snapshot(): EngineSnapshot
    override fun close()
}
```

`Candidate.id` 是本次候選快照識別，不作跨次輸入的永久 ID；UI 點擊必須帶上快照序號，由控制器拒絕過期點擊。頁碼、頁內索引、focus 均從 0 起算，UI 頁碼加一顯示。

計畫補充的容量決策：libchewing 0.12.0 標頭的 `MAX_PHONE_SEQ_LEN=50`、`MAX_PHRASE_LEN=11` 使其組字容量有上限。整合驗證版先限制 **32 個中文組字 code point**；達上限保留內容並提示先確認，不能依賴核心自動送出。這是需要使用者在本計畫審閱時確認的明示限制，並非已實作的行為。

## Task 1: 固定依賴、授權與可重現來源

**Files:** Create `third_party/chewing.lock.json`、`third_party/NOTICES.md`、`tools/verify_dependencies.py`、`tools/tests/test_verify_dependencies.py`、`native/build_chewing.sh`；Modify `.github/workflows/android-build.yml`。

**Interfaces:** Consumes 上游正式 tag 與逐份授權文件。Produces 經驗證的原始碼目錄與詞庫來源；lock 欄位為 `engineCommit`、`dataCommit`、`sourceSha256`、`dataSha256`、`licenses`、`redistributionApproved`、`reviewEvidence`。`verify_dependencies.py --lock PATH --release` 成功 exit 0，缺證據或雜湊不符 exit 1。

- [ ] 寫測試 `releaseRejectsUnreviewedDictionary`：`redistributionApproved=false` 必須 exit 1；`checksumMismatchIsRejected`：資料變更一 byte 必須 exit 1。缺失授權、未知來源及空 reviewEvidence 不得通過。
- [ ] 執行 `python3 -m unittest discover -s tools/tests -p 'test_verify_dependencies.py'`，確認尚無驗證器時失敗，再補最小驗證器並重跑。
- [ ] 以 libchewing **v0.12.0 / `05ae6bcb9309c466a1b32d69c146bc583be04747`** 為候選基準，固定其 data gitlink **`c44e81aef24b06f1509f19e1be54c99812d0c43f`**；實際下載後計算封存檔雜湊，核對上游發布的 checksum／簽章。資料未核對前不得把 approval 設為 true。
- [ ] 逐份盤點核心、詞庫、Rust 依賴及自行修改，保存授權全文與來源證據；此步驟若無法證實資料可再散布，即報告具體缺口並停止後續 APK 打包，不把未知狀態當作已批准。
- [ ] 建置工具固定 Rust 1.88.0、NDK 28.2.13676358 與 Cargo.lock；使用自有 JNI 橋接，保留 LGPL 元件的修改及可重建材料。不得使用浮動 branch 或省略相依元件清單。
- [ ] 重跑依賴驗證及雜湊破壞測試；成功才提交 `build: pin and document chewing engine dependencies`。此任務不宣稱已通過完整發行授權審查。

## Task 2: Android 原生核心、詞庫載入與生命週期

**Files:** Create `engine/EngineTypes.kt`、`ConversionEngine.kt`、`NativeChewing.kt`、`ChewingEngine.kt`、`DictionaryInstaller.kt`、`app/src/main/cpp/chewing_jni.cpp`、`app/src/main/cpp/CMakeLists.txt`；Modify `app/build.gradle.kts`；Test `app/src/androidTest/java/tw/zhuoran/ime/engine/ChewingEngineTest.kt`、`DictionaryInstallerTest.kt`。

**Interfaces:** Produces 上方共用介面定義的 `ConversionEngine`；`ChewingEngine.open(systemDirectory: File, userDirectory: File): ConversionEngine`；`DictionaryInstaller.install(assets: AssetManager, lock: File): File`。context 以 native handle 表示並拒絕已關閉 handle，字串複製到 Kotlin 後立即依 C API 規定釋放。

- [ ] 寫原生執行測試：`openLoadsRealDictionary` 讀取版本並確認不是 mini dictionary；插入 `Reading("ㄋㄧˇ")`、`Reading("ㄏㄠˇ")` 後，snapshot 有兩字且 committed 為空，候選含對應字詞。`autoLearningIsDisabled` 透過公開 getter 驗證模式。
- [ ] 執行 `gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=tw.zhuoran.ime.engine.ChewingEngineTest`，觀察原生庫尚未可載入的失敗。
- [ ] 交叉編譯 `chewing_capi` staticlib 並連結自有 JNI `.so`；ABI 為 arm64-v8a/x86_64，API 26，16 KB ELF 對齊。以獨立 host chewing-cli 建置詞庫，不能在 CI 主機執行 Android target binary。
- [ ] 實作公開介面；設定 `chewing_set_autoLearn(ctx, AUTOLEARN_DISABLED)` 並以 getter 核對；保留實際版本與資料 manifest。初始化明定候選每頁 10 項、後向字詞選擇、空白可開啟選字、Esc 不清整段、native 最大組字長度 39，控制器另外以 32 字容量守門。以固定版 API setter/getter 核對，不依賴預設值。每次操作消費一次 commit／aux buffer，不能重複讀出同一提交。
- [ ] `Insert` 僅接受標準化完整 Reading，以大千對應鍵餵入核心；失敗清除 native 未完成音節，不可改動先前 preedit、候選鎖定或提交文字。以 `invalidReadingPreservesPreviousPreedit` 驗證；無法達成時停下修正介面，不加第二份中文句子繞過。
- [ ] 寫並執行 `closeIsIdempotentAndRejectsLaterDispatch`、`damagedDictionaryRetainsPreviousVersion`、`interruptedInstallNeverPromotesPartialDirectory`：連續關閉不 crash，關閉後回 UNAVAILABLE；壞資料不切換 active 版本。所有載入及 context 存取序列化。
- [ ] 重跑此任務全部 instrumentation；以 `llvm-readelf -l` 核對每個 ELF LOAD 對齊至少 0x4000。成功提交 `feat: load pinned chewing engine on Android`。

## Task 3: 原生組字與局部候選的公開行為

**Files:** Modify `engine/ChewingEngine.kt`、`chewing_jni.cpp`；Create `engine/UnicodeOffsets.kt`；Test `app/src/androidTest/java/tw/zhuoran/ime/engine/ChewingEditingTest.kt`。

**Interfaces:** Consumes `ConversionEngine`；Produces所有 EngineCommand 的穩定語義。`UnicodeOffsets.toUtf16(text: String, codePointOffset: Int): Int` 負責唯一位置換算；native raw cursor 單位須以所選版實際行為驗證。

- [ ] 寫失敗測試 `continuousPhrasesRemainUncommitted`、`selectedCandidateSurvivesMoreInput`、`editInsideSentencePreservesAlignment`；分別驗證「你好」「我想去台北」「今天天氣很好」可連續組字、明確選字保留、句中刪改後文字與音節數相符。
- [ ] 執行此 instrumentation class 確認失敗，補上 Move、DeleteBefore、OpenCandidates、MoveCandidate、ChangePage、Choose、CloseCandidates 的適配。
- [ ] 句首候選對應第一字，句中／句尾對應游標左字；首字退格無操作。Choose 只更新 preedit，Commit 才回傳整段 committed；commit 讀出後不得再次出現。
- [ ] 寫 `candidatePagesClampAtBounds`：十項頁面、末頁不足十項、上一頁／下一頁越界留在原頁；無效 index 不變。若上游資料不足以形成 11 項候選，使用授權清楚的測試詞庫，不改正式詞庫來配合測試。
- [ ] 寫 `capacityLimitPreservesPreeditWithoutCommit`：組字 32 個 code point 後插入第 33 個，回 CAPACITY，preedit 不變、committed 為空。容量應在任何可能自動提交的 native 呼叫前檢查；獨立 Unicode 測試驗證 surrogate pair 只算一字。
- [ ] 執行 Task 2/3 全部 instrumentation、檢查無非預期 native 提交後，提交 `feat: support editable preedit and paged candidates`。

## Task 4: 共用輸入控制器與未完成音節

**Files:** Create `input/SyllableBuffer.kt`、`InputAction.kt`、`InputController.kt`；Modify `Zhuyin.kt`（只在必要時）；Test `app/src/test/java/tw/zhuoran/ime/input/SyllableBufferTest.kt`、`InputControllerTest.kt`。

**Interfaces:** `SyllableBuffer.push(symbol: Char): Boolean`、`backspace()`、`reading(tone: Char?): Reading?`、`clear()`、`display(): String`；不累積已完成音節。`InputController(engine: ConversionEngine)` 的 `handle(action: InputAction): InputResult` 回傳 `InputResult(snapshot: EngineSnapshot, pendingZhuyin: String, consumed: Boolean, commitText: String?, performEditorAction: Boolean, message: String?, revision: Long, mode: InputMode)`；`InputMode` 為 ZHUYIN、ENGLISH、SYMBOLS。InputAction 為 sealed interface：Symbol(symbol: Char)、Space、Enter、Backspace、Escape、Move(delta: Int)、OpenCandidates、NextPage、PreviousPage、Choose(index: Int, revision: Long)、Number(number: Int)、FunctionCandidate(number: Int)、SwitchMode(mode: InputMode)、Literal(text: String)、CommitRaw。沒有 payload 的項目為 data object。

- [ ] 將 v0.12 的亂序、替換、重複、退格及無效替換案例寫到 SyllableBufferTest；執行 `gradle :app:testDebugUnitTest --tests '*SyllableBufferTest'` 確認失敗，再實作最小音節容器並跑綠。
- [ ] 寫控制器測試：聲調或空白完成音節只產生 preedit；選字 Enter 不提交整句；組字 Enter 提交且 `performEditorAction=false`，下一個空白狀態 Enter 才為 true。以真實控制器搭配明確記憶體引擎測試替身驗證路由，不能用替身冒充 native 驗證。
- [ ] 執行 `gradle :app:testDebugUnitTest --tests '*InputControllerTest'`，確認失敗後實作四個互斥狀態：idle、pending reading、preedit、candidate selection；pending 可附著於已有 preedit 的游標。
- [ ] 狀態內實作 Esc 分層取消、退格、數字0–9／F1–F9、候選翻頁；無候選功能鍵不攔截。過期 Choose revision 必須不改狀態。
- [ ] 寫 `invalidReadingBlocksModeChangeUntilExplicitRawCommit`：無效音節保留，切英文／插標點不丟字；只有 CommitRaw 明確送注音。相同案例驗證觸控與物理事件路徑，並驗證候選關閉後退格刪一字。
- [ ] 執行全部 JVM tests，保留原 14 案例或逐項遷移其行為。提交 `feat: unify touch and physical composition actions`。

## Task 5: Android 編輯器與輸入生命週期

**Files:** Create `editor/AndroidEditorBridge.kt`、`input/PhysicalKeyMapper.kt`；Modify `ZhuoranImeService.kt`；Create instrumentation 測試用 Activity（只存在 androidTest）；Test `EditorBridgeTest.kt`、`PhysicalKeyMapperTest.kt`。

**Interfaces:** `AndroidEditorBridge.begin(connection: InputConnection, info: EditorInfo): Long` 回 sessionId；`apply(sessionId: Long, result: InputResult)`、`finish(sessionId: Long)`。`PhysicalKeyMapper.keyDown(event: KeyEvent): InputAction?`、`keyUp(event: KeyEvent): InputAction?`；只在完整獨立 Shift 按放時回傳 SwitchMode。

- [ ] 使用真實 EditText/BaseInputConnection 寫 `composingEnterNeverSendsMessage`、`emptyEnterHonorsEditorInfo`，驗證 SEND/SEARCH/NO_ENTER_ACTION；執行 instrumentation 看見失敗，再實作 editor 轉接。
- [ ] 組字使用 setComposingText 及明確選取位置，提交僅經此 bridge。引擎 code point 先轉 UTF-16；用 `supplementaryCharacterCursorAndDeletionStayAligned` 驗證罕用字旁游標及退格。
- [ ] 寫 `staleSessionCannotWriteToNewEditor`：A 欄位開始組字、切 B 後套用 A 結果，B 不變；`restartAndExternalSelectionDoNotDuplicatePreedit`：restart／外部游標移動後不得重複插字。連線失效時丟棄舊工作階段結果，清理原生 context，不將未確認文字寫到新連線。
- [ ] 寫 `passwordAndNumericFieldsBypassChineseEngine`：所有文字密碼變體、數字密碼、電話及一般數字欄位無中文組字或學習；保留英數、刪除、確認。文字框中的已有選取區域可被新輸入替換，不能刪到區域外。
- [ ] 寫 Shift 單按、Shift+A、Shift+Space、Ctrl+C、Alt+Tab、重複按鍵及 key-up 測試；保留現有 PhysicalKeys 的物理鍵位映射，無組字的導航鍵不攔截。
- [ ] 以同一 InputController 路徑接入 service，移除 service 內重複組字／提交判斷。執行 Task 2–5 全部測試，提交 `feat: connect editable preedit to Android editors`。

## Task 6: 候選列、觸控狀態與降級操作

**Files:** Create `CandidateBarView.kt`；Modify `TouchKeyboardView.kt`、`ZhuoranImeService.kt`；Test `TouchKeyboardIntegrationTest.kt`、`EngineUnavailableTest.kt`（androidTest）。

**Interfaces:** `CandidateBarView.render(result: InputResult)`、`onAction: (InputAction) -> Unit`；TouchKeyboardView 透過相同 callback 發送 InputAction，模式顯示只能由控制器結果決定，不能先改 UI 再發現切換失敗。

- [ ] 寫觸控流程測試：點注音→顯示中文 preedit→開候選→點第二候選→仍未提交→點 Enter 才提交。另驗證虛擬鍵盤與物理按鍵序列輸出完全一致。
- [ ] 執行 instrumentation 觀察失敗後，實作可點選的十候選、頁碼及上一頁／下一頁；候選不足一頁不顯示無效下一頁狀態。候選重畫不重建 native context。
- [ ] 顯示 pending 注音及中文組字，提供進出選字、句中左右移動、明確「送出注音原文」操作；延續既有標準注音鍵位及英文／完整 ASCII 符號頁。候選點選附 revision。
- [ ] 寫 `engineUnavailableStillAllowsEnglishAndImePicker` 與字型縮放、橫向螢幕測試；載入失敗用可理解的中文提示，不以 prototype 詞庫隱藏故障。驗證所有可見按鍵有正面積與可點擊範圍，記錄手機實測尚缺項目。
- [ ] 執行所有 JVM 及 Android 測試，提交 `feat: expose editable composition and candidate pages on touch keyboard`。

## Task 7: 可重現建置與交付證據

**Files:** Modify `.github/workflows/android-build.yml`、`app/build.gradle.kts`、`README.md`；Create `tools/check_apk.py`、`docs/validation/chewing-integration.md`；Test `tools/tests/test_check_apk.py`。

**Interfaces:** `check_apk.py APK --lock third_party/chewing.lock.json`：核對兩 ABI、資料雜湊、授權聲明、版本及 16 KB 對齊，違反任一條件 exit 1。版本為 **斲然注音 0.13.0 / versionCode 13**，與底層 libchewing 0.12.0 明確區分。

- [ ] 寫缺 ABI、缺詞庫、錯資料雜湊或錯 ELF 對齊的失敗測試，執行 `python3 -m unittest discover -s tools/tests`，先看到失敗再補驗證器。
- [ ] CI 順序：依賴證據與雜湊驗證→host 工具及詞庫→兩 ABI native→`gradle :app:testDebugUnitTest`→x86_64 Android instrumentation→`gradle :app:assembleDebug`→APK 內容核對。使用 emulator 的腳本正常退出 emulator，再交還 CI；不要把還在背景執行的測試當成通過。
- [ ] 核對 native 16 KB 對齊及 API 26 相容性；至少在 API 26 與 API 35 執行載入／生命週期測試。效能記錄冷載入及連續輸入時間，先建立基準，不編造未測得的速度。
- [ ] CI 有穩定私密簽章設定時才使用；沒有時可產出隔離的 debug APK供測試，但交付必須註明需移除舊版的可能性。禁止將私鑰寫進原始碼、log、ZIP 或公開 Artifact，也不以成功編譯代替覆蓋安裝驗證。
- [ ] 整理測試項目與結果、詞庫及核心來源雜湊、來源與授權材料、APK SHA-256、簽章指紋及與上一版是否一致。未做三星手機實測時列為未驗證，不宣稱通過。
- [ ] 執行完整檢查一次；成功後提交 `build: validate and package chewing integration preview`，建立／更新 draft PR並交付安裝檔。合併主分支另依使用者的整合指示處理。

## 執行方式與阻擋條件

建議 **Native**：由同一實作者循序執行，因為七項工作共享原生快照、游標及輸入狀態介面，頻繁換實作者會增加交接成本；完成後再做一次獨立程式審查。

Task 1 的來源／授權未通過、Task 2 無法保持原子拒絕、Task 3 無法避免自動提交，都是明確停止點。報告實際證據與替代方案，不自行把驗收條件降低後宣稱整合完成。

## 本次規劃查證來源

- libchewing 固定 tag：https://github.com/chewing/libchewing/tree/v0.12.0
- 固定版 C API：https://github.com/chewing/libchewing/blob/v0.12.0/capi/include/chewing.h （autoLearn setter/getter、候選／游標／提交及容量常數）
- 固定版 Cargo：https://github.com/chewing/libchewing/blob/v0.12.0/Cargo.toml
- 固定版 CMake：https://github.com/chewing/libchewing/blob/v0.12.0/CMakeLists.txt
- 原始碼 data gitlink：https://github.com/chewing/libchewing/tree/v0.12.0/data
- Android 16 KB：https://developer.android.com/guide/practices/page-sizes
- NDK r28：https://github.com/android/ndk/wiki/Changelog-r28
