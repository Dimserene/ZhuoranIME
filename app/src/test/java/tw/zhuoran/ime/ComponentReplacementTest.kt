package tw.zhuoran.ime

import org.junit.Assert.*
import org.junit.Test

class ComponentReplacementTest {
    private fun enter(symbols: String): Composer = Composer().also { c ->
        symbols.forEach { assertTrue("Rejected $it", c.push(it)) }
    }
    @Test fun newInitialReplacesOldInitialAndPreservesOtherComponents() {
        val c = enter("ㄅㄧㄢ")
        assertTrue(c.push('ㄆ'))
        assertEquals("ㄆㄧㄢ", c.display())
    }
    @Test fun newFinalReplacesOldFinal() {
        val c = enter("ㄅㄚ")
        assertTrue(c.push('ㄛ'))
        assertEquals("ㄅㄛ", c.display())
    }
    @Test fun newMedialReplacesOldMedial() {
        val c = enter("ㄅㄧㄢ")
        assertTrue(c.push('ㄨ'))
        assertEquals("ㄅㄨㄢ", c.display())
    }
    @Test fun repeatOfSameComponentDoesNotCreateAnotherBackspaceStep() {
        val c = enter("ㄅ")
        assertTrue(c.push('ㄅ'))
        assertEquals("ㄅ", c.display())
        c.backspace()
        assertFalse(c.hasInput)
    }
    @Test fun backspaceRemovesReplacementWithoutRevivingOldComponent() {
        val c = enter("ㄅㄚ")
        assertTrue(c.push('ㄛ'))
        c.backspace()
        assertEquals("ㄅ", c.display())
    }
    @Test fun replacementWorksAfterOutOfOrderEntry() {
        val c = enter("ㄤㄧㄒ")
        assertTrue(c.push('ㄋ'))
        assertEquals("ㄋㄧㄤ", c.display())
        assertTrue(c.push('ˊ'))
        assertEquals(listOf("ㄋㄧㄤˊ"), c.completed())
    }
    @Test fun replacementDoesNotChangeCompletedSyllables() {
        val c = enter("ㄋㄧˇㄅㄚ")
        assertTrue(c.push('ㄆ'))
        assertEquals("ㄋㄧˇ ㄆㄚ", c.display())
        assertTrue(c.push('ˊ'))
        assertEquals(listOf("ㄋㄧˇ", "ㄆㄚˊ"), c.completed())
    }
    @Test fun invalidReplacementLeavesOriginalCompositionIntact() {
        val c = enter("ㄐㄧ")
        assertFalse(c.push('ㄨ'))
        assertEquals("ㄐㄧ", c.display())
    }
}
