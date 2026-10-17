package tw.zhuoran.ime.engine
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class ChewingAndroidTest {
    private fun engine(): ChewingEngine {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dictionary=DictionaryInstaller(File(context.filesDir,"dictionaries")).install {context.assets.open("chewing/$it")}
        return ChewingEngine.open(dictionary,File(context.filesDir,"native-tests"))
    }
    @Test fun recordColdOpenAndThirtyTwoSyllables() {
        val start=android.os.SystemClock.elapsedRealtimeNanos()
        engine().use {e->
            val opened=android.os.SystemClock.elapsedRealtimeNanos()
            repeat(16) {assertTrue(e.dispatch(EngineCommand.Insert(Reading("ㄋㄧˇ"))).accepted);assertTrue(e.dispatch(EngineCommand.Insert(Reading("ㄏㄠˇ"))).accepted)}
            val done=android.os.SystemClock.elapsedRealtimeNanos()
            println("PERF coldOpenMs=${(opened-start)/1_000_000} insert32Ms=${(done-opened)/1_000_000}")
        }
    }
    @Test fun fullDictionaryAndLifecycle() {engine().use {e->
        assertTrue(e.dispatch(EngineCommand.Insert(Reading("ㄋㄧˇ"))).accepted)
        val r=e.dispatch(EngineCommand.Insert(Reading("ㄏㄠˇ")))
        assertEquals("你好",r.snapshot.preedit);assertEquals("",r.committed)
        assertTrue(e.dispatch(EngineCommand.OpenCandidates).snapshot.candidates!!.items.any {it.text=="好"})
        e.dispatch(EngineCommand.CloseCandidates)
        val before=e.snapshot();assertEquals(before,e.dispatch(EngineCommand.Insert(Reading("ㄅ"))).snapshot)
        assertEquals("你好",e.dispatch(EngineCommand.Commit).committed)
        assertEquals("",e.dispatch(EngineCommand.Commit).committed)
        e.close();e.close();assertEquals(EngineError.UNAVAILABLE,e.dispatch(EngineCommand.Clear).error)
    }}
}
