package tw.zhuoran.ime

/** Windows standard 41-key Zhuyin mapping, US QWERTY physical key labels. */
object Zhuyin {
    val keys = mapOf(
        '1' to 'ㄅ','q' to 'ㄆ','a' to 'ㄇ','z' to 'ㄈ',
        '2' to 'ㄉ','w' to 'ㄊ','s' to 'ㄋ','x' to 'ㄌ',
        'e' to 'ㄍ','d' to 'ㄎ','c' to 'ㄏ',
        'r' to 'ㄐ','f' to 'ㄑ','v' to 'ㄒ',
        '5' to 'ㄓ','t' to 'ㄔ','g' to 'ㄕ','b' to 'ㄖ',
        'y' to 'ㄗ','h' to 'ㄘ','n' to 'ㄙ',
        'u' to 'ㄧ','j' to 'ㄨ','m' to 'ㄩ',
        '8' to 'ㄚ','i' to 'ㄛ','k' to 'ㄜ',',' to 'ㄝ',
        '9' to 'ㄞ','o' to 'ㄟ','l' to 'ㄠ','.' to 'ㄡ',
        '0' to 'ㄢ','p' to 'ㄣ',';' to 'ㄤ','/' to 'ㄥ','-' to 'ㄦ',
        '6' to 'ˊ','3' to 'ˇ','4' to 'ˋ','7' to '˙'
    )
    private val initials = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ"
    private val medials = "ㄧㄨㄩ"
    private val finals = "ㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ"
    private val tones = "ˊˇˋ˙"
    /** Lightweight structural check, not a complete Mandarin syllable table. */
    fun isPlausible(s: String): Boolean {
        val base = s.filterNot { it in tones }
        if (base.isEmpty()) return false
        val initial = base.firstOrNull { it in initials }
        val medial = base.firstOrNull { it in medials }
        val final = base.firstOrNull { it in finals }
        // ㄐ/ㄑ/ㄒ only combine with ㄧ or ㄩ in standard Mandarin.
        if (initial != null && initial in "ㄐㄑㄒ" && medial != null && medial !in "ㄧㄩ") return false
        // These initials do not combine with ㄧ/ㄩ.
        if (initial != null && initial in "ㄓㄔㄕㄖㄗㄘㄙ" && medial != null && medial in "ㄧㄩ") return false
        return initial != null || medial != null || final != null
    }
    fun normalize(raw: String): String? {
        if (raw.isEmpty()) return null
        val groups = listOf(initials, medials, finals, tones)
        val parts = groups.map { g -> raw.filter { it in g } }
        if (parts.any { it.length > 1 } || parts.sumOf { it.length } != raw.length) return null
        return parts.joinToString("")
    }
}
