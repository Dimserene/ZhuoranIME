package tw.zhuoran.ime.input
import android.view.KeyEvent
import tw.zhuoran.ime.PhysicalKeys
import tw.zhuoran.ime.Zhuyin
class PhysicalKeyMapper {
 var mode=InputMode.ZHUYIN
 var hasComposition=false
 var selecting=false
 private var shiftKey:Int?=null
 private var shiftUsed=false
 fun reset(){shiftKey=null;shiftUsed=false}
 fun suppressRepeat(event:KeyEvent,consumed:Set<Int>):Boolean = event.repeatCount>0 && (event.keyCode==KeyEvent.KEYCODE_ENTER || event.keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER || (event.keyCode in consumed && event.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F9))
 fun keyDown(event:KeyEvent):InputAction? {
  val code=event.keyCode
  if(code==KeyEvent.KEYCODE_SHIFT_LEFT || code==KeyEvent.KEYCODE_SHIFT_RIGHT) {
   if(shiftKey!=null || event.repeatCount>0)shiftUsed=true else {shiftKey=code;shiftUsed=event.isCtrlPressed||event.isAltPressed||event.isMetaPressed}
   return null
  }
  if(shiftKey!=null)shiftUsed=true
  if(event.isCtrlPressed||event.isAltPressed||event.isMetaPressed)return null
  if(event.isShiftPressed && code==KeyEvent.KEYCODE_SPACE)return null
  when(code) {
   KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER->return InputAction.Enter
   KeyEvent.KEYCODE_SPACE->return InputAction.Space
   KeyEvent.KEYCODE_DEL->return if(hasComposition)InputAction.Backspace else null
   KeyEvent.KEYCODE_ESCAPE->return if(hasComposition)InputAction.Escape else null
   KeyEvent.KEYCODE_DPAD_LEFT->return if(hasComposition)InputAction.Move(-1) else null
   KeyEvent.KEYCODE_DPAD_RIGHT->return if(hasComposition)InputAction.Move(1) else null
   KeyEvent.KEYCODE_DPAD_UP->return if(selecting)InputAction.Move(-1) else null
   KeyEvent.KEYCODE_DPAD_DOWN->return if(selecting)InputAction.Move(1) else if(hasComposition)InputAction.OpenCandidates else null
   KeyEvent.KEYCODE_PAGE_UP->return if(selecting)InputAction.PreviousPage else null
   KeyEvent.KEYCODE_PAGE_DOWN->return if(selecting)InputAction.NextPage else null
  }
  if(code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F9)return if(selecting && event.repeatCount==0)InputAction.FunctionCandidate(code-KeyEvent.KEYCODE_F1+1) else null
  if(mode!=InputMode.ZHUYIN || event.isShiftPressed) {
   val unicode=event.unicodeChar
   return if(unicode>0 && !Character.isISOControl(unicode) && unicode and android.view.KeyCharacterMap.COMBINING_ACCENT==0)InputAction.Literal(String(Character.toChars(unicode))) else null
  }
  if(code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)return InputAction.Number(code-KeyEvent.KEYCODE_0)
  val physical=when(code) {KeyEvent.KEYCODE_SEMICOLON->';';KeyEvent.KEYCODE_COMMA->',';KeyEvent.KEYCODE_PERIOD->'.';KeyEvent.KEYCODE_SLASH->'/';KeyEvent.KEYCODE_MINUS->'-';else->PhysicalKeys.fromKeyCode(code)}
  physical?.let {Zhuyin.keys[it]}?.let {return InputAction.Symbol(it)}
  // Printable punctuation uses the controller too, so invalid pending readings cannot leak.
  val unicode=event.unicodeChar
  return if(unicode>0 && !Character.isISOControl(unicode) && unicode and android.view.KeyCharacterMap.COMBINING_ACCENT==0)InputAction.Literal(String(Character.toChars(unicode))) else null
 }
 fun keyUp(event:KeyEvent):InputAction? {
  if(event.keyCode!=shiftKey)return null
  val standalone=!shiftUsed;reset()
  return if(standalone)InputAction.SwitchMode(if(mode==InputMode.ZHUYIN)InputMode.ENGLISH else InputMode.ZHUYIN) else null
 }
}
