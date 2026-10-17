package tw.zhuoran.ime.input
import tw.zhuoran.ime.Zhuyin
import tw.zhuoran.ime.engine.Reading
class SyllableBuffer {
 private var raw=""
 private var tone:Char?=null
 fun push(symbol:Char):Boolean {
  if(symbol in "ˊˇˋ˙") {if(raw.isEmpty())return false;tone=symbol;return true}
  val replacement=Zhuyin.replaceComponent(raw,symbol)?:return false
  val normalized=Zhuyin.normalize(replacement)?:return false
  if(!Zhuyin.isPlausible(normalized))return false
  raw=replacement;tone=null;return true
 }
 fun backspace(){if(tone!=null)tone=null else if(raw.isNotEmpty())raw=raw.dropLast(1)}
 fun reading(tone:Char?=null):Reading? = Zhuyin.normalize(raw)?.let {Reading(it+(tone?:this.tone)?.toString().orEmpty())}
 fun display()=reading()?.zhuyin.orEmpty()
 fun clear(){raw="";tone=null}
}
