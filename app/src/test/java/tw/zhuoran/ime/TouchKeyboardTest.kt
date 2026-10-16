package tw.zhuoran.ime

import org.junit.Assert.*
import org.junit.Test

class TouchKeyboardTest {
    @Test fun touchKeysCanComposeOutOfOrderAndSelectCandidate() {
        val composer = Composer()
        val available = TouchLayout.zhuyinRows.flatten()
        for (key in "uv;3") {
            assertTrue("Touch keyboard is missing key: $key", key in available)
            assertTrue(composer.push(Zhuyin.keys.getValue(key)))
        }
        assertEquals(listOf("ㄒㄧㄤˇ"), composer.completed())
        val words = Candidates.parse("ㄒㄧㄤˇ\t想 響 享").lookup(composer.completed())
        assertEquals("響", CandidateSelection().choose(2, words))
    }
    @Test fun everyStandardZhuyinSymbolIsReachableExactlyOnce() {
        val keys = TouchLayout.zhuyinRows.flatten() + TouchLayout.erKey
        assertEquals(Zhuyin.keys.keys, keys.toSet())
        assertEquals(keys.size, keys.toSet().size)
    }
    @Test fun switchingToLiteralTextResolvesPendingComposition() {
        val composer = Composer()
        for (key in "uv;3") composer.push(Zhuyin.keys.getValue(key))
        val words = Candidates.parse("ㄒㄧㄤˇ\t想 響 享")
        assertEquals("想", TouchLayout.pendingText(composer, words, CandidateSelection()))
        val active = Composer()
        active.push('ㄋ'); active.push('ㄧ')
        assertEquals("ㄋㄧ", TouchLayout.pendingText(active, words, CandidateSelection()))
    }
    @Test fun touchCompositionSupportsFirstToneAndBackspace() {
        val composer = Composer()
        composer.push('ㄊ'); composer.push('ㄚ')
        assertTrue(composer.closeToneless())
        assertEquals(listOf("ㄊㄚ"), composer.completed())
        composer.backspace()
        assertTrue(composer.hasActive())
        composer.backspace()
        assertEquals("ㄊ", composer.display())
        composer.clear()
        assertFalse(composer.hasInput)
    }
}
