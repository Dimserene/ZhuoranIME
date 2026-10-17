package tw.zhuoran.ime
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import tw.zhuoran.ime.input.*
class CandidateBarView(context:Context):LinearLayout(context) {
 var onAction:(InputAction)->Unit={}
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 init {orientation=VERTICAL;setBackgroundColor(Color.rgb(250,235,225))}
 private fun button(row:LinearLayout,label:String,description:String=label,action:()->Unit) {
  row.addView(TextView(context).apply {
   text=label;contentDescription=description;textSize=17f;gravity=Gravity.CENTER
   setPadding(dp(12),dp(6),dp(12),dp(6));isClickable=true;isFocusable=true
   setTextColor(Color.rgb(75,60,50));setOnClickListener {action()}
  },LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT))
 }
 fun render(result:InputResult) {
  val emit=onAction
  removeAllViews()
  addView(TextView(context).apply {
   text=result.message ?: if(result.hasComposition)result.snapshot.preedit+"　"+result.pendingZhuyin else "斲然注音 0.13｜${when(result.mode){InputMode.ZHUYIN->"注音";InputMode.ENGLISH->"英文";InputMode.SYMBOLS->"符號"}}"
   textSize=14f;maxLines=2;setPadding(dp(8),dp(3),dp(8),dp(3));setTextColor(Color.rgb(75,60,50))
  },LayoutParams(-1,-2))
  val controls=LinearLayout(context)
  addView(HorizontalScrollView(context).apply {isHorizontalScrollBarEnabled=false;addView(controls)},LayoutParams(-1,dp(42)))
  if(result.hasComposition) {
   button(controls,"◀","組字游標左移"){emit(InputAction.Move(-1))}
   button(controls,"▶","組字游標右移"){emit(InputAction.Move(1))}
   button(controls,if(result.snapshot.candidates==null)"選字" else "關閉選字"){emit(if(result.snapshot.candidates==null)InputAction.OpenCandidates else InputAction.Escape)}
   if(result.pendingZhuyin.isNotEmpty())button(controls,"送出注音原文"){emit(InputAction.CommitRaw)}
  }
  result.snapshot.candidates?.let {page->
   if(page.page>0)button(controls,"上一頁"){emit(InputAction.PreviousPage)}
   button(controls,"${page.page+1}/${page.pages}","候選頁碼"){}
   if(page.page+1<page.pages)button(controls,"下一頁"){emit(InputAction.NextPage)}
   val row=LinearLayout(context)
   addView(HorizontalScrollView(context).apply {isHorizontalScrollBarEnabled=false;addView(row)},LayoutParams(-1,dp(48)))
   page.items.forEachIndexed {i,candidate->
    button(row,"${if(i==9)0 else i+1} ${candidate.text}","候選字 ${i+1}：${candidate.text}") {emit(InputAction.Choose(i,result.revision))}
    if(i==page.focus)row.getChildAt(i).setBackgroundColor(Color.rgb(255,213,184))
   }
  }
  controls.visibility=if(controls.childCount==0)GONE else VISIBLE
 }
}
