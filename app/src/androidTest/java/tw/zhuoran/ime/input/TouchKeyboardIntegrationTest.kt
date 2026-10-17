package tw.zhuoran.ime.input
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import tw.zhuoran.ime.engine.*
class TouchKeyboardIntegrationTest {
 private fun controller():InputController {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val dictionary=DictionaryInstaller(File(context.filesDir,"touch-tests-dictionary")).install {context.assets.open("chewing/$it")}
  return InputController(ChewingEngine.open(dictionary,File(context.filesDir,"touch-tests-user")))
 }
 @Test fun touchAndPhysicalActionsHaveIdenticalComposition() {
  controller().use {touch->controller().use {physical->
   "ㄋㄧˇㄏㄠˇ".forEach {touch.handle(InputAction.Symbol(it))}
   val mapper=PhysicalKeyMapper()
   listOf(KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_U,KeyEvent.KEYCODE_3,KeyEvent.KEYCODE_C,KeyEvent.KEYCODE_L,KeyEvent.KEYCODE_3).forEach {physical.handle(mapper.keyDown(KeyEvent(KeyEvent.ACTION_DOWN,it))!!)}
   assertEquals(touch.current().snapshot,physical.current().snapshot)
   val choices=touch.handle(InputAction.OpenCandidates);val chosen=touch.handle(InputAction.Choose(1,choices.revision))
   assertNull(chosen.commitText);assertTrue(chosen.snapshot.preedit.isNotEmpty())
   assertNotNull(touch.handle(InputAction.Enter).commitText)
  }}
 }
 @Test fun capacityRejectionStillAllowsEnterToCommit() {
  controller().use {c->
   repeat(16) {"ㄋㄧˇㄏㄠˇ".forEach {c.handle(InputAction.Symbol(it))}}
   val rejected=c.handle(InputAction.Symbol('ㄏ'));assertEquals("",rejected.pendingZhuyin);assertNotNull(rejected.message)
   val confirmed=c.handle(InputAction.Enter);assertEquals(32,confirmed.commitText!!.codePointCount(0,confirmed.commitText.length));assertFalse(confirmed.performEditorAction)
  }
 }
 @Test fun engineUnavailableStillAllowsEnglishAndImePicker() {
  InputController(UnavailableEngine(),false).use {c->
   assertEquals(InputMode.ENGLISH,c.current().mode);assertNotNull(c.current().message)
   assertEquals("abc",c.handle(InputAction.Literal("abc")).commitText)
   assertEquals(InputMode.ENGLISH,c.handle(InputAction.SwitchMode(InputMode.ZHUYIN)).mode)
  }
  // The picker is a permanent keyboard key, independent of the engine/controller.
 }
}
