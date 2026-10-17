package tw.zhuoran.ime
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class TouchViewTest {
 @Test fun landscapeLargeTextKeysAndUnavailablePickerRemainUsable() {
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
   val base=InstrumentationRegistry.getInstrumentation().targetContext
   val context=base.createConfigurationContext(Configuration(base.resources.configuration).apply {fontScale=1.5f;orientation=Configuration.ORIENTATION_LANDSCAPE})
   var picked=0;var requested:TouchMode?=null
   val keyboard=TouchKeyboardView(context,{},{},{},{},{},{},{picked++},{requested=it})
   keyboard.start(false,false,"確認")
   keyboard.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
   keyboard.layout(0,0,960,keyboard.measuredHeight)
   fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
   val keys=views(keyboard).filter {it.isClickable && it.visibility==View.VISIBLE}
   assertTrue(keys.size>=41);keys.forEach {assertTrue(it.width>0 && it.height>0)}
   val symbolKeys=keys.filterIsInstance<android.widget.TextView>().filter {key->key.text.any {it in Zhuyin.keys.values}}
   assertEquals(41,symbolKeys.size)
   symbolKeys.forEach {key->
    val offset=key.text.indexOfFirst {it in Zhuyin.keys.values}
    val line=key.layout.getLineForOffset(offset)
    assertTrue("Zhuyin glyph must not be clipped",key.layout.getLineBottom(line)<=key.height-key.totalPaddingTop-key.totalPaddingBottom)
   }
   val bitmap=android.graphics.Bitmap.createBitmap(keyboard.width,keyboard.height,android.graphics.Bitmap.Config.ARGB_8888)
   keyboard.draw(android.graphics.Canvas(bitmap))
   java.io.File(context.filesDir,"keyboard-landscape.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   keys.first {it.contentDescription=="切換輸入法"}.performClick();assertEquals(1,picked)
   keys.first {it.contentDescription=="ABC"}.performClick();assertEquals(TouchMode.ENGLISH,requested)
   assertTrue(keyboard.isZhuyin) // waits for the controller to accept mode change
   keyboard.renderMode(TouchMode.ENGLISH);assertFalse(keyboard.isZhuyin)
  }
 }
}
