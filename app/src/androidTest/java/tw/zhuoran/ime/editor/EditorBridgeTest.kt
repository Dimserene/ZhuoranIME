package tw.zhuoran.ime.editor
import android.text.Editable
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import tw.zhuoran.ime.engine.EngineSnapshot
import tw.zhuoran.ime.input.InputResult
class EditorBridgeTest {
 private class Connection(val edit:EditText):BaseInputConnection(edit,true) {
  var actions=0;var enters=0
  override fun getEditable():Editable=edit.editableText
  override fun performEditorAction(actionCode:Int):Boolean {actions++;return true}
  override fun sendKeyEvent(event:KeyEvent):Boolean {if(event.action==KeyEvent.ACTION_DOWN)enters++;return true}
 }
 private fun ui(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
 private fun connection()=Connection(EditText(InstrumentationRegistry.getInstrumentation().targetContext).apply {setText("");setSelection(0)})
 private fun info()=EditorInfo().apply {inputType=InputType.TYPE_CLASS_TEXT;imeOptions=EditorInfo.IME_ACTION_SEND;initialSelStart=0;initialSelEnd=0}
 @Test fun composingEnterNeverSendsMessage()=ui {
  val c=connection();val bridge=AndroidEditorBridge();val id=bridge.begin(c,info())
  bridge.apply(id,InputResult(EngineSnapshot("你好",2),""))
  bridge.apply(id,InputResult(EngineSnapshot(),"",commitText="你好"))
  assertEquals("你好",c.edit.text.toString());assertEquals(0,c.actions);assertEquals(0,c.enters)
 }
 @Test fun emptyEnterHonorsEditorInfo()=ui {
  val c=connection();val bridge=AndroidEditorBridge();val info=info();val id=bridge.begin(c,info)
  bridge.apply(id,InputResult(EngineSnapshot(),"",performEditorAction=true));assertEquals(1,c.actions)
  info.imeOptions=EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
  val second=bridge.begin(c,info);bridge.apply(second,InputResult(EngineSnapshot(),"",performEditorAction=true));assertEquals(1,c.actions);assertEquals(1,c.enters)
 }
 @Test fun staleSessionCannotWriteToNewEditor()=ui {
  val a=connection();val b=connection();val bridge=AndroidEditorBridge();val old=bridge.begin(a,info());bridge.begin(b,info())
  bridge.apply(old,InputResult(EngineSnapshot(),"",commitText="秘密"));assertEquals("",b.edit.text.toString())
 }
 @Test fun supplementaryCharacterCursorAndDeletionStayAligned()=ui {
  val c=connection();val bridge=AndroidEditorBridge();val id=bridge.begin(c,info())
  bridge.apply(id,InputResult(EngineSnapshot("𠮷好",1),""));assertEquals(2,c.edit.selectionStart)
  bridge.apply(id,InputResult(EngineSnapshot(),"",commitText="𠮷"));bridge.deleteBefore(id);assertEquals("",c.edit.text.toString())
 }
 @Test fun restartAndExternalSelectionDoNotDuplicatePreedit()=ui {
  val c=connection();val bridge=AndroidEditorBridge();val old=bridge.begin(c,info())
  bridge.apply(old,InputResult(EngineSnapshot("你好",2),""));bridge.finish(old)
  val id=bridge.begin(c,info().apply {initialSelStart=2;initialSelEnd=2})
  bridge.apply(old,InputResult(EngineSnapshot(),"",commitText="你好"));assertEquals("你好",c.edit.text.toString())
  bridge.apply(id,InputResult(EngineSnapshot("好",1),""))
  assertFalse(bridge.acceptSelection(id,0,0));bridge.finish(id);assertEquals("你好好",c.edit.text.toString())
 }
 @Test fun passwordAndNumericFieldsBypassChineseEngine() {
  listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD).forEach {assertTrue(EditorPolicy.bypassChinese(InputType.TYPE_CLASS_TEXT or it))}
  listOf(InputType.TYPE_CLASS_NUMBER,InputType.TYPE_CLASS_PHONE,InputType.TYPE_CLASS_DATETIME).forEach {assertTrue(EditorPolicy.bypassChinese(it))}
  assertFalse(EditorPolicy.bypassChinese(InputType.TYPE_CLASS_TEXT))
 }
}
