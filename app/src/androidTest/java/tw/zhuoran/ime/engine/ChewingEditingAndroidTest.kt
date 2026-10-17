package tw.zhuoran.ime.engine
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class ChewingEditingAndroidTest {
 private fun dictionary():File {
  val c=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
  return DictionaryInstaller(File(c.filesDir,"editing-dictionary")).install {c.assets.open("chewing/$it")}
 }
 private fun engine()=ChewingEngine.open(dictionary(),File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"editing-user"))
 private fun ConversionEngine.insert(vararg readings:String) {readings.forEach {val r=dispatch(EngineCommand.Insert(Reading(it)));assertTrue(it,r.accepted);assertEquals("",r.committed)}}
 @Test fun continuousPhrasesRemainUncommitted() {engine().use {e->
  e.insert("ㄋㄧˇ","ㄏㄠˇ");assertEquals("你好",e.snapshot().preedit);e.dispatch(EngineCommand.Clear)
  e.insert("ㄨㄛˇ","ㄒㄧㄤˇ","ㄑㄩˋ","ㄊㄞˊ","ㄅㄟˇ");assertEquals(5,e.snapshot().preedit.codePointCount(0,e.snapshot().preedit.length));e.dispatch(EngineCommand.Clear)
  e.insert("ㄐㄧㄣ","ㄊㄧㄢ","ㄊㄧㄢ","ㄑㄧˋ","ㄏㄣˇ","ㄏㄠˇ");assertEquals("今天天氣很好",e.snapshot().preedit)
 }}
 @Test fun selectedCandidateSurvivesMoreInput() {engine().use {e->
  e.insert("ㄋㄧˇ");val page=e.dispatch(EngineCommand.OpenCandidates).snapshot.candidates!!
  val index=page.items.indexOfFirst {it.text=="妳"};assertTrue(index>=0)
  val r=e.dispatch(EngineCommand.Choose(index));assertTrue(r.accepted);assertEquals("",r.committed)
  e.insert("ㄏㄠˇ");assertEquals("妳好",e.snapshot().preedit)
 }}
 @Test fun editInsideSentencePreservesAlignment() {engine().use {e->
  e.insert("ㄋㄧˇ","ㄏㄠˇ");assertEquals(1,e.dispatch(EngineCommand.Move(-1)).snapshot.cursorCodePoint)
  val r=e.dispatch(EngineCommand.DeleteBefore);assertTrue(r.accepted);assertEquals("好",r.snapshot.preedit);assertEquals(0,r.snapshot.cursorCodePoint)
  assertEquals(r.snapshot,e.dispatch(EngineCommand.DeleteBefore).snapshot)
  e.insert("ㄋㄧˇ");assertEquals("你好",e.snapshot().preedit)
 }}
 @Test fun candidatePagesClampAtBounds() {engine().use {e->
  e.insert("ㄧˋ");val first=e.dispatch(EngineCommand.OpenCandidates).snapshot.candidates!!
  assertEquals(10,first.items.size);assertTrue(first.pages>1)
  assertEquals(first,e.dispatch(EngineCommand.ChangePage(-1)).snapshot.candidates)
  val last=e.dispatch(EngineCommand.ChangePage(1000)).snapshot.candidates!!
  assertEquals(last.pages-1,last.page);assertTrue(last.items.size in 1..10)
  assertEquals(last,e.dispatch(EngineCommand.ChangePage(1)).snapshot.candidates)
  val before=e.snapshot();assertFalse(e.dispatch(EngineCommand.Choose(99)).accepted);assertEquals(before,e.snapshot())
 }}
 @Test fun capacityLimitPreservesPreeditWithoutCommit() {engine().use {e->
  repeat(32){e.insert("ㄋㄧˇ")};val before=e.snapshot();val r=e.dispatch(EngineCommand.Insert(Reading("ㄏㄠˇ")))
  assertEquals(EngineError.CAPACITY,r.error);assertEquals(before,r.snapshot);assertEquals("",r.committed)
  assertEquals(before.preedit,e.dispatch(EngineCommand.Commit).committed);assertEquals("",e.dispatch(EngineCommand.Commit).committed)
 }}
 @Test fun everyPackagedReadingIsAtomicAndOneCodePoint() {engine().use {e->
  File(dictionary(),"readings.txt").readLines().forEach {reading->
   e.dispatch(EngineCommand.Clear);val r=e.dispatch(EngineCommand.Insert(Reading(reading)))
   assertTrue(reading,r.accepted);assertEquals(reading,1,r.snapshot.preedit.codePointCount(0,r.snapshot.preedit.length));assertEquals("",r.committed)
  }
 }}
 @Test fun supplementaryCandidateUsesCodePointCursor() {engine().use {e->
  e.insert("ㄅㄣ");var page=e.dispatch(EngineCommand.OpenCandidates).snapshot.candidates!!
  var found=false
  while(true) {
   val index=page.items.indexOfFirst {it.text=="𩣺"}
   if(index>=0) {
    val r=e.dispatch(EngineCommand.Choose(index));assertTrue(r.accepted);assertEquals("𩣺",r.snapshot.preedit);assertEquals(1,r.snapshot.cursorCodePoint);found=true;break
   }
   if(page.page+1>=page.pages)break
   page=e.dispatch(EngineCommand.ChangePage(1)).snapshot.candidates!!
  }
  assertTrue("supplementary-plane dictionary candidate",found)
  assertEquals(0,e.dispatch(EngineCommand.Move(-1)).snapshot.cursorCodePoint)
  assertEquals(1,e.dispatch(EngineCommand.Move(1)).snapshot.cursorCodePoint)
  assertEquals("",e.dispatch(EngineCommand.DeleteBefore).snapshot.preedit)
  assertEquals(2,UnicodeOffsets.toUtf16("𩣺好",1))
 }}

}
