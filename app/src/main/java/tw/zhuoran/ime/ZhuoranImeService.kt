package tw.zhuoran.ime

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView

/** Physical keyboard IME proof of concept. Not yet a production IME. */
class ZhuoranImeService : InputMethodService() {
    private val composer = Composer()
    private val selection = CandidateSelection()
    private lateinit var lexicon: Candidates
    private var status: TextView? = null
    private var candidatesView: TextView? = null
    private var secureField = false
    private var editorComposing = false

    override fun onCreate() {
        super.onCreate()
        lexicon = Candidates.parse(assets.open("lexicon.tsv").bufferedReader().use { it.readText() })
    }
    override fun onCreateCandidatesView(): View = TextView(this).apply {
        setBackgroundColor(Color.rgb(245, 235, 225))
        setTextColor(Color.BLACK)
        textSize = 15f
        setPadding(20, 12, 20, 12)
        candidatesView = this
        refresh()
    }
    override fun onCreateInputView(): View = TextView(this).apply {
        setBackgroundColor(Color.rgb(245, 235, 225))
        setTextColor(Color.BLACK)
        textSize = 16f
        setPadding(20, 16, 20, 16)
        status = this
        refresh()
    }
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
            candidatesView?.text = ""
            return
        }
        val opts = options()
        val displayText = if (!composer.hasInput) "斲然注音 v0.10｜實體鍵盤測試" else
            "注音：${composer.display()}\n" +
            (if (composer.hasActive()) "組字中：聲調結束音節；無聲調按空白"
             else if (opts.isEmpty()) "測試詞庫無候選字（Enter 可提交注音）"
             else opts.take(9).mapIndexed { i, word -> "${if (i == selection.index) "▶" else ""}F${i + 1}:$word" }.joinToString("  "))
        status?.text = displayText
        candidatesView?.text = displayText
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
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (secureField) return super.onKeyDown(keyCode, event)
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
