package tw.zhuoran.ime.input
import tw.zhuoran.ime.engine.EngineSnapshot

enum class InputMode { ZHUYIN, ENGLISH, SYMBOLS }
sealed interface InputAction {
 companion object {
  /** Numeric standard-key labels select candidates only while the controller is selecting. */
  fun zhuyinKey(symbol:Char):InputAction {
   val physical=tw.zhuoran.ime.Zhuyin.keys.entries.firstOrNull {it.value==symbol}?.key
   return if(physical!=null && physical in '0'..'9')Number(physical-'0') else Symbol(symbol)
  }
 }
 data class Symbol(val symbol:Char):InputAction
 data object Space:InputAction
 data object Enter:InputAction
 data object Backspace:InputAction
 data object Escape:InputAction
 data class Move(val delta:Int):InputAction
 data object OpenCandidates:InputAction
 data object NextPage:InputAction
 data object PreviousPage:InputAction
 data class Choose(val index:Int,val revision:Long):InputAction
 data class Number(val number:Int):InputAction
 data class FunctionCandidate(val number:Int):InputAction
 data class SwitchMode(val mode:InputMode):InputAction
 data class Literal(val text:String):InputAction
 data object CommitRaw:InputAction
}
data class InputResult(
 val snapshot:EngineSnapshot,
 val pendingZhuyin:String,
 val consumed:Boolean=true,
 val commitText:String?=null,
 val performEditorAction:Boolean=false,
 val message:String?=null,
 val revision:Long=0,
 val mode:InputMode=InputMode.ZHUYIN
) {val hasComposition get()=snapshot.preedit.isNotEmpty() || pendingZhuyin.isNotEmpty()}
