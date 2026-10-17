package tw.zhuoran.ime.editor
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import tw.zhuoran.ime.engine.UnicodeOffsets
import tw.zhuoran.ime.input.InputResult

object EditorPolicy {
 fun bypassChinese(type:Int):Boolean {
  val kind=type and InputType.TYPE_MASK_CLASS;val variation=type and InputType.TYPE_MASK_VARIATION
  return kind in listOf(InputType.TYPE_CLASS_NUMBER,InputType.TYPE_CLASS_PHONE,InputType.TYPE_CLASS_DATETIME) ||
   (kind==InputType.TYPE_CLASS_TEXT && variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
 }
}
/** All editor writes are synchronous and fenced to one InputConnection generation. */
class AndroidEditorBridge {
 private var generation=0L
 private var connection:InputConnection?=null
 private var info=EditorInfo()
 private var composing=false
 private var start=0
 private var expectedStart=0
 private var expectedEnd=0
 fun begin(connection:InputConnection,info:EditorInfo):Long {
  finish(generation);generation++;this.connection=connection;this.info=info
  start=minOf(info.initialSelStart,info.initialSelEnd).coerceAtLeast(0)
  expectedStart=info.initialSelStart.coerceAtLeast(0);expectedEnd=info.initialSelEnd.coerceAtLeast(0)
  return generation
 }
 fun apply(sessionId:Long,result:InputResult) {
  if(sessionId!=generation)return
  val ic=connection?:return
  ic.beginBatchEdit()
  try {
   if(!composing) {
    val extracted=ic.getExtractedText(ExtractedTextRequest(),0)
    start=if(extracted!=null)extracted.startOffset+minOf(extracted.selectionStart,extracted.selectionEnd) else minOf(expectedStart,expectedEnd)
   }
   if(result.commitText!=null) {
    ic.commitText(result.commitText,1);composing=false
    expectedStart=start+result.commitText.length;expectedEnd=expectedStart
   } else if(result.hasComposition) {
    val preedit=result.snapshot.preedit
    val cursor=UnicodeOffsets.toUtf16(preedit,result.snapshot.cursorCodePoint)
    val display=preedit.substring(0,cursor)+result.pendingZhuyin+preedit.substring(cursor)
    ic.setComposingText(display,1);composing=true
    expectedStart=start+cursor+result.pendingZhuyin.length;expectedEnd=expectedStart
    ic.setSelection(expectedStart,expectedEnd)
   } else if(composing) {
    ic.setComposingText("",1);ic.finishComposingText();composing=false
    expectedStart=start;expectedEnd=start
   }
   if(result.performEditorAction) {
    val action=info.imeOptions and EditorInfo.IME_MASK_ACTION
    val allowed=info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION==0
    if(!(allowed && action !in listOf(EditorInfo.IME_ACTION_NONE,EditorInfo.IME_ACTION_UNSPECIFIED) && ic.performEditorAction(action))) {
     ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER))
    }
   }
  } finally {ic.endBatchEdit()}
 }
 /** External selection detaches composition before any subsequent native snapshot is applied. */
 fun acceptSelection(sessionId:Long,newStart:Int,newEnd:Int):Boolean {
  if(sessionId!=generation)return false
  if(composing && (newStart!=expectedStart || newEnd!=expectedEnd))return false
  expectedStart=newStart.coerceAtLeast(0);expectedEnd=newEnd.coerceAtLeast(0);return true
 }
 fun deleteBefore(sessionId:Long) {
  if(sessionId!=generation)return
  val ic=connection?:return
  if(!ic.getSelectedText(0).isNullOrEmpty())ic.commitText("",1)
  else if(!ic.deleteSurroundingTextInCodePoints(1,0)) {
   val text=ic.getTextBeforeCursor(2,0)?.toString().orEmpty()
   val size=if(text.length>=2 && Character.isSurrogatePair(text[text.length-2],text.last()))2 else 1
   ic.deleteSurroundingText(size,0)
  }
 }
 fun finish(sessionId:Long) {
  if(sessionId!=generation)return
  if(composing)connection?.finishComposingText()
  composing=false;connection=null;generation++
 }
}
