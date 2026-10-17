package tw.zhuoran.ime

/** Shares standard physical key positions and composition rules with the touch UI. */
object TouchLayout {
    val zhuyinRows: List<List<Char>> = listOf("1234567890", "qwertyuiop", "asdfghjkl;", "zxcvbnm,./").map { it.toList() }
    val erKey: Char = '-'
    fun pendingText(composer: Composer, lexicon: Candidates, selection: CandidateSelection): String {
        if (!composer.hasInput) return ""
        if (composer.hasActive()) composer.closeToneless()
        return selection.selected(lexicon.lookup(composer.completed())) ?: composer.display()
    }
}
