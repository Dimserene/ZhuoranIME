import tw.zhuoran.ime.*
import java.io.File
class CoreRegressionTest {
@org.junit.Test fun legacyCoreScenarios() {
    val lexicon = Candidates.parse(File("src/main/assets/lexicon.tsv").readText())
    fun enter(keys: String): Composer {
        val c = Composer()
        keys.forEach { k -> check(c.push(Zhuyin.keys[k] ?: error("No key: $k"))) { "Rejected $k in $keys" } }
        return c
    }
    for (keys in listOf("vu;3", "uv;3", ";vu3")) {
        val c = enter(keys)
        check(c.completed() == listOf("ㄒㄧㄤˇ"))
        check(lexicon.lookup(c.completed()).first() == "想")
    }
    val phrase = enter("ji3vu;3")
    check(phrase.completed() == listOf("ㄨㄛˇ", "ㄒㄧㄤˇ"))
    check(lexicon.lookup(phrase.completed()).first() == "我想")
    phrase.backspace()
    check(phrase.hasActive())
    val selection = CandidateSelection()
    val opts = lexicon.lookup(listOf("ㄒㄧㄤˇ"))
    selection.move(1, opts.size)
    check(selection.selected(opts) == "響")
    check(selection.choose(3, opts) == "享")
    selection.move(100, opts.size)
    check(selection.selected(opts) == "享")
    selection.reset()
    check(selection.selected(opts) == "想")
    check(lexicon.lookup(listOf("ㄆㄚˇ")).isEmpty())
    check(Zhuyin.keys[';'] == 'ㄤ')
    check(Zhuyin.keys['0'] == 'ㄢ')
    check(Zhuyin.normalize("ㄧㄒㄤˇ") == "ㄒㄧㄤˇ")
    check(Zhuyin.normalize("ㄤㄒㄧˇ") == "ㄒㄧㄤˇ")
    check(Zhuyin.normalize("ㄒㄒㄧ") == null)
    val invalid = Composer()
    check(invalid.push('ㄒ'))
    check(!invalid.push('ㄨ'))
    check(invalid.display() == "ㄒ")
    check(PhysicalKeys.fromKeyCode(49) == 'u')
    check(PhysicalKeys.fromKeyCode(50) == 'v')
    check(PhysicalKeys.fromKeyCode(10) == '3')
    check(PhysicalKeys.fromKeyCode(999) == null)
    println("PASS: normalization, multi-syllable composition, TSV lexicon, selection")
}

}
