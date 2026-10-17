package tw.zhuoran.ime
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.io.File
import tw.zhuoran.ime.engine.*
import tw.zhuoran.ime.editor.*
import tw.zhuoran.ime.input.*

/** Offline IME: touch and physical events share one synchronous controller. */
class ZhuoranImeService:InputMethodService() {
 private var controller:InputController?=null
 private val bridge=AndroidEditorBridge()
 private val mapper=PhysicalKeyMapper()
 private var session=0L
 private var bypass=false
 private var bar:CandidateBarView?=null
 private var keyboard:TouchKeyboardView?=null
 private var actionLabel="↵"
 private val consumedKeys=mutableSetOf<Int>()
 private fun send(action:InputAction):Boolean {
  val c=controller?:return false
  val result=c.handle(action)
  if(action==InputAction.Backspace && !result.consumed)bridge.deleteBefore(session)
  else if(result.consumed)bridge.apply(session,result)
  render(result)
  return result.consumed
 }
 private fun render(result:InputResult) {
  mapper.mode=result.mode;mapper.hasComposition=result.hasComposition;mapper.selecting=result.snapshot.candidates!=null
  keyboard?.renderMode(TouchMode.valueOf(result.mode.name));bar?.render(result)
 }
 override fun onCreateCandidatesView():View=CandidateBarView(this).also {view->
  bar=view
  val generation=session
  view.onAction={action->if(session==generation)send(action)}
  controller?.current()?.let(::render)
 }
 override fun onCreateInputView():View=TouchKeyboardView(this,
  onSymbol={send(InputAction.zhuyinKey(it))},onText={send(InputAction.Literal(it))},
  onSpace={send(InputAction.Space)},onDelete={send(InputAction.Backspace)},
  onEnter={send(InputAction.Enter)},onCancel={send(InputAction.Escape)},
  onSwitchIme={(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()},
  onModeChange={send(InputAction.SwitchMode(InputMode.valueOf(it.name)))}
 ).also {keyboard=it;it.start(bypass,false,actionLabel);controller?.current()?.let(::render)}
 override fun onStartInput(attribute:EditorInfo?,restarting:Boolean) {
  super.onStartInput(attribute,restarting)
  bridge.finish(session);controller?.close();controller=null;mapper.reset();consumedKeys.clear()
  val info=attribute?:EditorInfo()
  bypass=EditorPolicy.bypassChinese(info.inputType)
  var available=!bypass
  val engine:ConversionEngine=if(bypass)UnavailableEngine() else try {
   val installer=DictionaryInstaller(File(filesDir,"dictionaries"))
   val dictionary=try {installer.install {assets.open("chewing/$it")}} catch(_:Exception) {installer.active()?:throw IllegalStateException("No verified dictionary")}
   ChewingEngine.open(dictionary,File(filesDir,"chewing-user"))
  } catch(_:Exception) {available=false;UnavailableEngine()} catch(_:LinkageError) {available=false;UnavailableEngine()}
  controller=InputController(engine,available)
  if(bypass)controller!!.reset(InputMode.ENGLISH)
  val ic=currentInputConnection
  if(ic!=null)session=bridge.begin(ic,info)
  actionLabel=when(info.imeOptions and EditorInfo.IME_MASK_ACTION) {EditorInfo.IME_ACTION_DONE->"完成";EditorInfo.IME_ACTION_GO->"前往";EditorInfo.IME_ACTION_NEXT->"下一步";EditorInfo.IME_ACTION_SEARCH->"搜尋";EditorInfo.IME_ACTION_SEND->"傳送";else->"↵"}
  keyboard?.start(bypass,false,actionLabel)
  val generation=session
  bar?.onAction={action->if(session==generation)send(action)}
  setCandidatesViewShown(!bypass)
  render(controller!!.current())
 }
 override fun onFinishInput() {
  bridge.finish(session);controller?.close();controller=null;mapper.reset();consumedKeys.clear();setCandidatesViewShown(false)
  super.onFinishInput()
 }
 override fun onDestroy(){controller?.close();bridge.finish(session);super.onDestroy()}
 override fun onUpdateSelection(oldSelStart:Int,oldSelEnd:Int,newSelStart:Int,newSelEnd:Int,candidatesStart:Int,candidatesEnd:Int) {
  super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
  if(!bridge.acceptSelection(session,newSelStart,newSelEnd)) {
   bridge.finish(session)
   controller?.reset(if(bypass)InputMode.ENGLISH else mapper.mode)
   currentInputConnection?.let {
    val updated=EditorInfo().apply { currentInputEditorInfo?.let {old->inputType=old.inputType;imeOptions=old.imeOptions};initialSelStart=newSelStart;initialSelEnd=newSelEnd }
    session=bridge.begin(it,updated)
   }
   val generation=session;bar?.onAction={action->if(session==generation)send(action)}
   controller?.current()?.let(::render)
  }
 }
 override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
  if(mapper.suppressRepeat(event,consumedKeys))return true
  val action=mapper.keyDown(event)?:return super.onKeyDown(keyCode,event)
  if(send(action)){consumedKeys+=keyCode;return true}
  return super.onKeyDown(keyCode,event)
 }
 override fun onKeyUp(keyCode:Int,event:KeyEvent):Boolean {
  mapper.keyUp(event)?.let {if(!bypass && send(it))return true}
  if(consumedKeys.remove(keyCode))return true
  return super.onKeyUp(keyCode,event)
 }
}
