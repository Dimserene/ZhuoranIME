package tw.zhuoran.ime

/** Android KEYCODE_A..Z and KEYCODE_0..9 are physical US-QWERTY positions.
 * This pure Kotlin mapping is intentionally separate from Android APIs for tests.
 */
object PhysicalKeys {
    fun fromKeyCode(code: Int): Char? = when (code) {
        in 29..54 -> ('a'.code + code - 29).toChar()
        in 7..16 -> ('0'.code + code - 7).toChar()
        else -> null
    }
}
