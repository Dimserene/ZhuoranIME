package tw.zhuoran.ime.input
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
class PhysicalKeyMapperTest {
 @Test fun heldEnterCannotBecomeSendAfterCommit() {
  val mapper=PhysicalKeyMapper()
  for(code in listOf(KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER)) {
   assertFalse(mapper.suppressRepeat(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0),emptySet()))
   assertTrue(mapper.suppressRepeat(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,1),setOf(code)))
   assertTrue(mapper.suppressRepeat(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,1),emptySet()))
   assertFalse(mapper.suppressRepeat(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0),emptySet()))
  }
 }
 @Test fun independentShiftOnlyChangesMode() {
  val mapper=PhysicalKeyMapper();mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT))
  assertEquals(InputAction.SwitchMode(InputMode.ENGLISH),mapper.keyUp(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_SHIFT_LEFT)))
  mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT));mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A))
  assertNull(mapper.keyUp(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_SHIFT_LEFT)))
 }
 @Test fun unmappedPrintablePunctuationUsesTheController() {
  val mapper=PhysicalKeyMapper();mapper.hasComposition=true
  assertNull(mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB)))
  assertEquals(InputAction.Literal("="),mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_EQUALS)))
  assertEquals(InputAction.Literal("["),mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_LEFT_BRACKET)))
 }
 @Test fun shortcutsAndIdleNavigationPassThrough() {
  val mapper=PhysicalKeyMapper();assertNull(mapper.keyDown(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_C,0,KeyEvent.META_CTRL_ON)))
  assertNull(mapper.keyDown(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB,0,KeyEvent.META_ALT_ON)))
  assertNull(mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT)))
  assertEquals(InputAction.Number(1),mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_1)))
 }
}
