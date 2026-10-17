package tw.zhuoran.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

internal enum class TouchMode { ZHUYIN, ENGLISH, SYMBOLS }

/** Android views only: all composition stays in the service's shared Composer. */
internal class TouchKeyboardView(
    context: Context,
    private val onSymbol: (Char) -> Unit,
    private val onText: (String) -> Unit,
    private val onSpace: () -> Unit,
    private val onDelete: () -> Unit,
    private val onEnter: () -> Unit,
    private val onCancel: () -> Unit,
    private val onSwitchIme: () -> Unit,
    private val onModeChange: () -> Unit
) : LinearLayout(context) {
    private var mode = TouchMode.ZHUYIN
    val isZhuyin: Boolean get() = mode == TouchMode.ZHUYIN
    private var upper = false
    private var secure = false
    private var enterLabel = "↵"
    private val ink = Color.rgb(60, 48, 43)
    private val accent = Color.rgb(255, 213, 184)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(250, 235, 225))
        setPadding(dp(3), dp(3), dp(3), dp(5))
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        build()
    }

    fun start(secureField: Boolean, numeric: Boolean, action: String) {
        secure = secureField
        mode = if (numeric) TouchMode.SYMBOLS else if (secure) TouchMode.ENGLISH else TouchMode.ZHUYIN
        upper = false
        enterLabel = action
        build()
    }

    private fun switchTo(next: TouchMode) {
        onModeChange()
        mode = next
        build()
    }

    private fun row(height: Int): LinearLayout = LinearLayout(context).also {
        it.gravity = Gravity.CENTER
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, dp(height)))
    }

    private fun key(row: LinearLayout, label: CharSequence, description: String = label.toString(), weight: Float = 1f,
                    special: Boolean = false, action: () -> Unit) {
        row.addView(TextView(context).apply {
            text = label
            textSize = if (label.length > 2 && !label.contains('\n')) 13f else 18f
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(ink)
            contentDescription = description
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(if (special) accent else Color.WHITE)
                cornerRadius = dp(7).toFloat()
            }
            setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); action() }
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
    }

    private fun build() {
        removeAllViews()
        val height = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 36 else 49
        val tools = row(32)
        key(tools, "⌨", "切換輸入法", special = true, action = onSwitchIme)
        if (!secure) {
            key(tools, "取消", "取消目前注音", special = true, action = onCancel)
            key(tools, "，", action = { onText(if (mode == TouchMode.ZHUYIN) "，" else ",") })
            key(tools, "。", action = { onText(if (mode == TouchMode.ZHUYIN) "。" else ".") })
        }
        when (mode) {
            TouchMode.ZHUYIN -> TouchLayout.zhuyinRows.forEach { keys ->
                val r = row(height)
                keys.forEach { physical ->
                    val symbol = Zhuyin.keys.getValue(physical)
                    val label = SpannableString("$physical\n$symbol").apply {
                        setSpan(RelativeSizeSpan(0.5f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    key(r, label, symbol.toString(), action = { onSymbol(symbol) })
                }
            }
            TouchMode.ENGLISH -> {
                listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { letters ->
                    val r = row(height)
                    letters.forEach { c ->
                        val label = if (upper) c.uppercaseChar().toString() else c.toString()
                        key(r, label, action = { onText(label) })
                    }
                }
            }
            TouchMode.SYMBOLS -> listOf("1234567890", "@#\u0024%&*()-+", "!\"':;/?=_.", "[]{}<>\\|~`").forEach { symbols ->
                val r = row(height)
                symbols.forEach { c -> key(r, c.toString(), action = { onText(c.toString()) }) }
            }
        }
        val bottom = row(height + 3)
        key(bottom, if (mode == TouchMode.SYMBOLS) (if (secure) "ABC" else "注音") else "?123", special = true,
            action = { switchTo(if (mode == TouchMode.SYMBOLS && !secure) TouchMode.ZHUYIN else if (mode == TouchMode.SYMBOLS) TouchMode.ENGLISH else TouchMode.SYMBOLS) })
        key(bottom, if (mode == TouchMode.ZHUYIN || secure) "ABC" else "注音", special = true,
            action = { switchTo(if (mode == TouchMode.ZHUYIN || secure) TouchMode.ENGLISH else TouchMode.ZHUYIN) })
        key(bottom, if (mode == TouchMode.ZHUYIN) "組字／空白" else "空白", weight = 2.7f, action = onSpace)
        when (mode) {
            TouchMode.ZHUYIN -> key(bottom, "ㄦ", action = { onSymbol(Zhuyin.keys.getValue(TouchLayout.erKey)) })
            TouchMode.ENGLISH -> key(bottom, if (upper) "⇧●" else "⇧", "切換英文大小寫", special = true, action = { upper = !upper; build() })
            TouchMode.SYMBOLS -> key(bottom, "ABC", special = true, action = { switchTo(TouchMode.ENGLISH) })
        }
        key(bottom, "⌫", "刪除", special = true, action = onDelete)
        key(bottom, enterLabel, "確認／${enterLabel}", special = true, action = onEnter)
    }
}
