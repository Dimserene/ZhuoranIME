package tw.zhuoran.ime.engine
object UnicodeOffsets {
 fun toUtf16(text:String,codePointOffset:Int):Int = text.offsetByCodePoints(0,codePointOffset.coerceIn(0,text.codePointCount(0,text.length)))
}
