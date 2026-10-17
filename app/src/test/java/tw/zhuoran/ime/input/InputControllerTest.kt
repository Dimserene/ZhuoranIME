package tw.zhuoran.ime.input
import org.junit.Assert.*
import org.junit.Test
import tw.zhuoran.ime.engine.*
/** A routing test double, deliberately not evidence about native conversion. */
private class MemoryEngine: ConversionEngine {
 var state=EngineSnapshot();var inserts=0
 override fun snapshot()=state
 override fun close(){}
 override fun dispatch(command:EngineCommand):EngineReply {
  var commit=""
  when(command) {
   is EngineCommand.Insert->{if(command.reading.zhuyin !in listOf("ㄋㄧˇ","ㄏㄠˇ"))return EngineReply(state,false,error=EngineError.INVALID_READING);inserts++;state=EngineSnapshot(state.preedit+if(command.reading.zhuyin=="ㄋㄧˇ")"你" else "好",state.cursorCodePoint+1)}
   EngineCommand.OpenCandidates->state=state.copy(candidates=CandidatePage(listOf(Candidate(0,"你"),Candidate(1,"妳")),0,1,0))
   is EngineCommand.Choose->state=state.copy(preedit="妳",candidates=null)
   EngineCommand.CloseCandidates->state=state.copy(candidates=null)
   EngineCommand.Commit->{commit=state.preedit;state=EngineSnapshot()}
   EngineCommand.Clear->state=EngineSnapshot()
   EngineCommand.DeleteBefore->state=EngineSnapshot()
   else->{}
  };return EngineReply(state,true,commit)
 }
}
class InputControllerTest {
 private fun InputController.ni(){"ㄋㄧˇ".forEach {handle(InputAction.Symbol(it))}}
 @Test fun conversionEnterNeverPerformsEditorAction() {
  val c=InputController(MemoryEngine());c.ni();val r=c.handle(InputAction.Enter)
  assertEquals("你",r.commitText);assertFalse(r.performEditorAction);assertTrue(c.handle(InputAction.Enter).performEditorAction)
 }
 @Test fun candidateEnterOnlyUpdatesPreeditAndStaleTapIsRejected() {
  val c=InputController(MemoryEngine());c.ni();val r=c.handle(InputAction.OpenCandidates)
  val stale=c.handle(InputAction.Choose(1,r.revision-1));assertEquals("你",stale.snapshot.preedit)
  val chosen=c.handle(InputAction.Choose(1,stale.revision));assertNull(chosen.commitText);assertEquals("妳",chosen.snapshot.preedit)
  assertEquals("妳",c.handle(InputAction.Enter).commitText)
 }
 @Test fun invalidReadingBlocksModeChangeUntilExplicitRawCommit() {
  val c=InputController(MemoryEngine());c.handle(InputAction.Symbol('ㄅ'));c.handle(InputAction.Space)
  val r=c.handle(InputAction.SwitchMode(InputMode.ENGLISH));assertEquals(InputMode.ZHUYIN,r.mode);assertEquals("ㄅ",r.pendingZhuyin);assertNull(r.commitText)
  assertNull(c.handle(InputAction.Literal("，")).commitText);assertEquals("ㄅ",c.handle(InputAction.CommitRaw).commitText)
  assertEquals(InputMode.ENGLISH,c.handle(InputAction.SwitchMode(InputMode.ENGLISH)).mode)
 }
 @Test fun escapeClearsPendingBeforePreedit() {
  val c=InputController(MemoryEngine());c.ni();c.handle(InputAction.Symbol('ㄏ'));val r=c.handle(InputAction.Escape)
  assertEquals("你",r.snapshot.preedit);assertEquals("",r.pendingZhuyin);assertEquals("",c.handle(InputAction.Escape).snapshot.preedit)
 }
 @Test fun candidateNumberSelectsButIdleNumberIsZhuyin() {
  val c=InputController(MemoryEngine());assertEquals("ㄅ",c.handle(InputAction.Number(1)).pendingZhuyin)
  c.handle(InputAction.Escape);c.ni();c.handle(InputAction.OpenCandidates);assertEquals("妳",c.handle(InputAction.Number(2)).snapshot.preedit)
  assertFalse(c.handle(InputAction.FunctionCandidate(1)).consumed)
 }
 @Test fun touchNumberKeyMatchesPhysicalCandidateSelection() {
  val c=InputController(MemoryEngine());c.ni();c.handle(InputAction.OpenCandidates)
  val result=c.handle(InputAction.zhuyinKey('ㄉ'))
  assertEquals("妳",result.snapshot.preedit);assertEquals("",result.pendingZhuyin);assertNull(result.commitText)
 }
 @Test fun fullCompositionRejectsNewSyllableBeforePendingSoEnterCanConfirm() {
  val e=MemoryEngine();e.state=EngineSnapshot("你".repeat(32),32)
  val c=InputController(e);val r=c.handle(InputAction.Symbol('ㄏ'))
  assertEquals("",r.pendingZhuyin);assertEquals("你".repeat(32),r.snapshot.preedit);assertNotNull(r.message)
  val confirm=c.handle(InputAction.Enter);assertEquals("你".repeat(32),confirm.commitText);assertFalse(confirm.performEditorAction)
 }
}
