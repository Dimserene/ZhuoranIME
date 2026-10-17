package tw.zhuoran.ime.input
import tw.zhuoran.ime.Zhuyin
import tw.zhuoran.ime.engine.*

class InputController(private val engine:ConversionEngine,private val available:Boolean=true):AutoCloseable {
 private val pending=SyllableBuffer()
 private var mode=if(available)InputMode.ZHUYIN else InputMode.ENGLISH
 private var revision=0L
 private var message:String?=null
 fun current()=result()
 private fun result(consumed:Boolean=true,commit:String?=null,editor:Boolean=false)=InputResult(engine.snapshot(),pending.display(),consumed,commit,editor,message?:if(!available)"中文引擎無法載入，請先用英文或切換輸入法" else null,revision,mode)
 private fun dispatch(command:EngineCommand):EngineReply = engine.dispatch(command).also {
  if(!it.accepted)message=when(it.error) {EngineError.CAPACITY->"組字已達 32 字，請先按確認";EngineError.INVALID_READING->"這個注音無法轉字，請修改或送出注音原文";else->"中文引擎暫時無法處理，請取消或切換輸入法"}
 }
 private fun finishReading():Boolean {
  val reading=pending.reading()?:return true
  val r=dispatch(EngineCommand.Insert(reading));if(r.accepted)pending.clear()
  return r.accepted
 }
 private fun commitComposition():String? {
  if(!finishReading())return null
  val r=dispatch(EngineCommand.Commit);return if(r.accepted)r.committed else null
 }
 private fun choose(index:Int):InputResult {dispatch(EngineCommand.Choose(index));return result()}
 fun handle(action:InputAction):InputResult {
  if(action is InputAction.Choose && action.revision!=revision)return result()
  revision++;message=null
  val state=engine.snapshot()
  val has=state.preedit.isNotEmpty() || pending.display().isNotEmpty()
  when(action) {
   is InputAction.Symbol->{
    if(mode!=InputMode.ZHUYIN || !available)return result(false)
    if(pending.display().isEmpty() && state.preedit.codePointCount(0,state.preedit.length)>=32) {
     message="組字已達 32 字，請先按確認";return result()
    }
    if(state.candidates!=null)dispatch(EngineCommand.CloseCandidates)
    if(!pending.push(action.symbol))message="請修改目前的注音"
    else if(action.symbol in "ˊˇˋ˙")finishReading()
   }
   InputAction.Space->{
    if(mode!=InputMode.ZHUYIN)return result(commit=" ")
    if(pending.display().isNotEmpty())finishReading()
    else if(state.candidates!=null)dispatch(EngineCommand.ChangePage(1))
    else if(state.preedit.isNotEmpty())dispatch(EngineCommand.OpenCandidates)
    else return result(commit=" ")
   }
   InputAction.Enter->{
    if(state.candidates!=null)return choose(state.candidates.focus)
    if(has)return result(commit=commitComposition())
    return result(editor=true)
   }
   InputAction.Backspace->{
    if(pending.display().isNotEmpty())pending.backspace()
    else if(state.preedit.isNotEmpty()) {if(state.candidates!=null)dispatch(EngineCommand.CloseCandidates);dispatch(EngineCommand.DeleteBefore)}
    else return result(false)
   }
   InputAction.Escape->{
    if(pending.display().isNotEmpty())pending.clear()
    else if(state.candidates!=null)dispatch(EngineCommand.CloseCandidates)
    else if(state.preedit.isNotEmpty())dispatch(EngineCommand.Clear)
    else return result(false)
   }
   is InputAction.Move->{
    if(!has)return result(false)
    if(!finishReading())return result()
    dispatch(if(state.candidates!=null)EngineCommand.MoveCandidate(action.delta) else EngineCommand.Move(action.delta))
   }
   InputAction.OpenCandidates->{if(!has)return result(false);if(finishReading())dispatch(EngineCommand.OpenCandidates)}
   InputAction.NextPage,InputAction.PreviousPage->{if(state.candidates==null)return result(false);dispatch(EngineCommand.ChangePage(if(action==InputAction.NextPage)1 else -1))}
   is InputAction.Choose->{if(state.candidates==null)return result(false);return choose(action.index)}
   is InputAction.Number->{
    if(action.number !in 0..9)return result(false)
    if(state.candidates!=null)return choose(if(action.number==0)9 else action.number-1)
    if(mode!=InputMode.ZHUYIN)return result(commit=action.number.toString())
    val symbol=Zhuyin.keys.getValue(('0'.code+action.number).toChar())
    return handle(InputAction.Symbol(symbol))
   }
   is InputAction.FunctionCandidate->{if(state.candidates==null || action.number !in 1..9)return result(false);return choose(action.number-1)}
   is InputAction.SwitchMode->{
    if(action.mode==InputMode.ZHUYIN && !available)return result()
    val commit=if(has)commitComposition()?:return result() else null
    mode=action.mode;return result(commit=commit)
   }
   is InputAction.Literal->{val commit=if(has)commitComposition()?:return result() else "";return result(commit=commit+action.text)}
   InputAction.CommitRaw->{
    if(!has)return result(false)
    val offset=UnicodeOffsets.toUtf16(state.preedit,state.cursorCodePoint)
    val text=state.preedit.substring(0,offset)+pending.display()+state.preedit.substring(offset)
    dispatch(EngineCommand.Clear);pending.clear();return result(commit=text)
   }
  }
  return result()
 }
 fun reset(mode:InputMode=InputMode.ZHUYIN) {pending.clear();engine.dispatch(EngineCommand.Clear);this.mode=if(!available && mode==InputMode.ZHUYIN)InputMode.ENGLISH else mode;revision++;message=null}
 override fun close(){pending.clear();engine.close();revision++}
}
