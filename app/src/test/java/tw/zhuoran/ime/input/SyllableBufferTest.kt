package tw.zhuoran.ime.input
import org.junit.Assert.*
import org.junit.Test
class SyllableBufferTest {
 @Test fun outOfOrderReplacementAndBackspace() {
  val s=SyllableBuffer();"ㄠㄋ".forEach {assertTrue(s.push(it))};assertEquals("ㄋㄠ",s.display())
  assertTrue(s.push('ㄏ'));assertEquals("ㄏㄠ",s.display());assertTrue(s.push('ㄏ'));s.backspace();assertEquals("ㄠ",s.display())
  s.push('ㄨ');s.push('ㄧ');assertEquals("ㄧㄠ",s.display());s.backspace();assertEquals("ㄠ",s.display())
 }
 @Test fun invalidReplacementPreservesPreviousSyllable() {
  val s=SyllableBuffer();s.push('ㄐ');s.push('ㄧ');assertFalse(s.push('ㄨ'));assertEquals("ㄐㄧ",s.display())
  assertEquals("ㄐㄧˇ",s.reading('ˇ')!!.zhuyin);s.clear();assertNull(s.reading(null))
 }
}
