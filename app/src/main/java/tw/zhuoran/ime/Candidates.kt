package tw.zhuoran.ime

/** Offline lexicon loader; TSV lines: space-separated Zhuyin syllables, then candidates. */
class Candidates(private val entries: Map<String, List<String>>) {
    fun lookup(syllables: List<String>): List<String> = entries[syllables.joinToString(" ")].orEmpty()
    companion object {
        fun parse(tsv: String): Candidates {
            val map = linkedMapOf<String, List<String>>()
            tsv.lineSequence().forEach { line ->
                val fields = line.trim().split('\t')
                if (fields.size >= 2 && !fields[0].startsWith('#')) {
                    val key = fields[0].trim()
                    val values = fields.drop(1).flatMap { it.split(' ') }.filter { it.isNotBlank() }.distinct()
                    if (key.isNotEmpty() && values.isNotEmpty()) map[key] = values
                }
            }
            return Candidates(map)
        }
    }
}
