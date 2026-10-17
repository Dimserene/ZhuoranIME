package tw.zhuoran.ime

/** Offline prototype: tone marks close a syllable; space explicitly closes a toneless syllable. */
class Composer {
    private val finished = mutableListOf<String>()
    private val active = StringBuilder()
    val hasInput: Boolean get() = finished.isNotEmpty() || active.isNotEmpty()
    fun clear() { finished.clear(); active.clear() }
    fun backspace() {
        if (active.isNotEmpty()) active.deleteCharAt(active.lastIndex)
        else if (finished.isNotEmpty()) active.append(finished.removeAt(finished.lastIndex))
    }
    fun push(symbol: Char): Boolean {
        if (symbol in "ˊˇˋ˙") {
            if (active.isEmpty()) return false
            val proposed = Zhuyin.normalize(active.toString() + symbol) ?: return false
            if (!Zhuyin.isPlausible(proposed)) return false
            finished.add(proposed)
            active.clear()
            return true
        }
        if (symbol !in Zhuyin.keys.values) return false
        val updated = Zhuyin.replaceComponent(active.toString(), symbol) ?: return false
        val proposed = Zhuyin.normalize(updated) ?: return false
        if (!Zhuyin.isPlausible(proposed)) return false
        active.clear()
        active.append(updated)
        return true
    }
    fun closeToneless(): Boolean {
        if (active.isEmpty()) return false
        val normalized = Zhuyin.normalize(active.toString()) ?: return false
        if (!Zhuyin.isPlausible(normalized)) return false
        finished.add(normalized)
        active.clear()
        return true
    }
    fun syllables(): List<String> = finished + listOfNotNull(active.takeIf { it.isNotEmpty() }?.let { Zhuyin.normalize(it.toString()) })
    fun display(): String = syllables().joinToString(" ")
    fun completed(): List<String> = finished.toList()
    fun hasActive(): Boolean = active.isNotEmpty()
}
