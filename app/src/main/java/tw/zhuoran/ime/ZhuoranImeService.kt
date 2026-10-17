package tw.zhuoran.ime

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.view.Gravity

/** Offline touch and physical keyboard IME prototype. */
class ZhuoranImeService : InputMethodService() {
    private val composer = Composer()
    private val selection = CandidateSelection()
    private lateinit var lexicon: Candidates
    private var status: TextView? = null
    private var candidateRow: LinearLayout? = null
    private var candidateScroll: HorizontalScrollView? = null
    private var keyboard: TouchKeyboardView? = null
    private var numericField = false
    private var actionLabel = "↵"
    private var secureField = false
    private var editorComposing = false

    override fun onCreate() {
        super.onCreate()
        lexicon = Candidates.parse(assets.open("lexicon.tsv").bufferedReader().use { it.readText() })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onCreateCandidatesView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(250, 235, 225))
        status = TextView(this@ZhuoranImeService).apply {
            setTextColor(Color.rgb(75, 60, 50))
            textSize = 13f
            maxLines = 2
            setPadding(dp(10), dp(5), dp(10), dp(3))
        }
        addView(status)
        candidateRow = LinearLayout(this@ZhuoranImeService)
        candidateScroll = HorizontalScrollView(this@ZhuoranImeService).apply {
            isHorizontalScrollBarEnabled = false
            addView(candidateRow)
        }
        addView(candidateScroll, LinearLayout.LayoutParams(-1, dp(43)))
        refresh()
    }
    override fun onCreateInputView(): View = TouchKeyboardView(
        this, onSymbol = ::pushSymbol, onText = ::literalText,
        onSpace = ::touchSpace, onDelete = ::touchDelete, onEnter = ::touchEnter,
        onCancel = ::cancel,
        onSwitchIme = { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() },
        onModeChange = ::resolvePending
    ).also { keyboard = it; it.start(secureField, numericField, actionLabel) }
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composer.clear()
        selection.reset()
        editorComposing = false
        val inputType = attribute?.inputType ?: 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        secureField = (inputClass == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
             variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        numericField = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE || inputClass == InputType.TYPE_CLASS_DATETIME
        actionLabel = when (attribute?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_DONE -> "完成"
            EditorInfo.IME_ACTION_GO -> "前往"
            EditorInfo.IME_ACTION_NEXT -> "下一步"
            EditorInfo.IME_ACTION_SEARCH -> "搜尋"
            EditorInfo.IME_ACTION_SEND -> "傳送"
            else -> "↵"
        }
        keyboard?.start(secureField, numericField, actionLabel)
        setCandidatesViewShown(!secureField)
        refresh()
    }
    override fun onFinishInput() {
        super.onFinishInput()
        composer.clear()
        selection.reset()
        editorComposing = false
        setCandidatesViewShown(false)
    }
    private fun options(): List<String> = if (composer.hasActive()) emptyList() else lexicon.lookup(composer.completed())
    private fun refresh() {
        if (secureField) {
            status?.text = "密碼欄位：注音組字停用"
            candidateRow?.removeAllViews()
            return
        }
        val opts = options()
        status?.text = when {
            !composer.hasInput -> "斲然注音 v0.11｜點按注音，或使用外接鍵盤"
            composer.hasActive() -> "${composer.display()}　空白結束一聲音節"
            opts.isEmpty() -> "${composer.display()}　測試詞庫無候選字"
            else -> composer.display()
        }
        candidateRow?.removeAllViews()
        candidateScroll?.visibility = View.VISIBLE
        opts.take(9).forEachIndexed { i, word ->
            candidateRow?.addView(TextView(this).apply {
                text = word
                textSize = 22f
                gravity = Gravity.CENTER
                contentDescription = "候選字 ${i + 1}：$word"
                setTextColor(if (i == selection.index) Color.rgb(140, 62, 25) else Color.BLACK)
                setPadding(dp(14), dp(4), dp(14), dp(4))
                isClickable = true
                isFocusable = true
                setOnClickListener { commit(word) }
            }, LinearLayout.LayoutParams(-2, -1))
        }
        if (composer.hasInput) {
            currentInputConnection?.setComposingText(composer.display(), 1)
            editorComposing = true
        } else if (editorComposing) {
            currentInputConnection?.finishComposingText()
            editorComposing = false
        }
    }
    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        editorComposing = false
        composer.clear()
        selection.reset()
        refresh()
    }
    private fun cancel() {
        composer.clear()
        selection.reset()
        if (editorComposing) {
            currentInputConnection?.setComposingText("", 1)
            currentInputConnection?.finishComposingText()
            editorComposing = false
        }
        refresh()
    }
    private fun pushSymbol(symbol: Char) {
        if (secureField) return
        if (composer.push(symbol)) { selection.reset(); refresh() }
    }
    private fun resolvePending() {
        if (composer.hasInput) commit(TouchLayout.pendingText(composer, lexicon, selection))
    }
    private fun literalText(text: String) {
        resolvePending()
        currentInputConnection?.commitText(text, 1)
    }
    private fun touchSpace() {
        if (composer.hasActive()) { composer.closeToneless(); selection.reset(); refresh() }
        else if (composer.hasInput) resolvePending()
        else currentInputConnection?.commitText(" ", 1)
    }
    private fun touchDelete() {
        if (composer.hasInput) {
            composer.backspace()
            selection.reset()
            if (!composer.hasInput) cancel() else refresh()
            return
        }
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
        else if (!ic.deleteSurroundingTextInCodePoints(1, 0)) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val count = if (before.length >= 2 && Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])) 2 else 1
            ic.deleteSurroundingText(count, 0)
        }
    }
    private fun touchEnter() {
        if (composer.hasInput) { resolvePending(); return }
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val allowAction = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        if (allowAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (currentInputConnection?.performEditorAction(action) == true) return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (secureField || numericField || keyboard?.isZhuyin == false) return super.onKeyDown(keyCode, event)
        if (event.repeatCount > 0 && composer.hasInput && keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F9) return true
        // F1–F9 select candidates without stealing the number keys used by Zhuyin.
        // Reserve all modified shortcuts for Android/the foreground application.
        if (composer.hasInput && !composer.hasActive() &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed &&
            !event.isShiftPressed && keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F9) {
            val chosen = selection.choose(keyCode - KeyEvent.KEYCODE_F1 + 1, options())
            if (chosen != null) commit(chosen)
            return true
        }
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return super.onKeyDown(keyCode, event)
        if (composer.hasInput) {
            when (keyCode) {
                KeyEvent.KEYCODE_DEL -> { composer.backspace(); selection.reset(); if (!composer.hasInput) cancel() else refresh(); return true }
                KeyEvent.KEYCODE_ESCAPE -> { cancel(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> if (!composer.hasActive()) { selection.move(-1, options().size); refresh(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> if (!composer.hasActive()) { selection.move(1, options().size); refresh(); return true }
                KeyEvent.KEYCODE_SPACE -> {
                    if (composer.hasActive()) { composer.closeToneless(); selection.reset(); refresh() }
                    else commit(selection.selected(options()) ?: composer.display())
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    if (composer.hasActive()) composer.closeToneless()
                    commit(selection.selected(options()) ?: composer.display())
                    return true
                }
            }

        }
        // Do not let shifted keys bypass a pending composition and leak into the editor.
        if (event.isShiftPressed) return if (composer.hasInput) true else super.onKeyDown(keyCode, event)
        // Android keyboard layouts can report punctuation differently. Prefer physical
        // key codes for the keys essential to standard Zhuyin; use unicode for letters.
        val physical = when (keyCode) {
            KeyEvent.KEYCODE_SEMICOLON -> ';'
            KeyEvent.KEYCODE_COMMA -> ','
            KeyEvent.KEYCODE_PERIOD -> '.'
            KeyEvent.KEYCODE_SLASH -> '/'
            KeyEvent.KEYCODE_MINUS -> '-'
            else -> null
        }
        // Prefer Android key codes for US-QWERTY physical positions, even when the
        // Android keyboard language/layout changes the Unicode character.
        val key = physical ?: PhysicalKeys.fromKeyCode(keyCode)
        val symbol = key?.let(Zhuyin.keys::get)
            ?: return if (composer.hasInput) true else super.onKeyDown(keyCode, event)
        if (composer.push(symbol)) { selection.reset(); refresh(); return true }
        // Never leak a rejected Zhuyin key into the editor while composing.
        // Consume rejected mapped keys, including at the start of a syllable.
        // Otherwise a failed syllable could insert stray Latin text into the editor.
        return true
    }
}
