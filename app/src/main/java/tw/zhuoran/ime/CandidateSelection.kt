package tw.zhuoran.ime

/** Pure selection state, independent of Android UI. */
class CandidateSelection {
    var index: Int = 0
        private set
    fun reset() { index = 0 }
    fun move(delta: Int, count: Int) {
        index = if (count == 0) 0 else (index + delta).coerceIn(0, count - 1)
    }
    fun choose(number: Int, candidates: List<String>): String? = candidates.getOrNull(number - 1)
    fun selected(candidates: List<String>): String? = candidates.getOrNull(index)
}
