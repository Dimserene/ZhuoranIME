package tw.zhuoran.ime.engine

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ChewingEngineTest {
    private fun engine(): ChewingEngine = ChewingEngine.open(File(System.getProperty("zhuoran.test.dictionary", "../vendor/dictionary")), Files.createTempDirectory("chewing-test-user").toFile())
    private fun ConversionEngine.insert(reading: String) = dispatch(EngineCommand.Insert(Reading(reading)))
    @Test fun openLoadsRealDictionary() {
        engine().use { e ->
            assertTrue(e.insert("ㄋㄧˇ").accepted)
            val r=e.insert("ㄏㄠˇ")
            assertEquals("你好",r.snapshot.preedit)
            assertEquals("",r.committed)
            val page=e.dispatch(EngineCommand.OpenCandidates).snapshot.candidates!!
            assertTrue(page.items.any { it.text=="好" })
        }
    }
    @Test fun invalidReadingPreservesPreviousPreedit() {
        engine().use { e ->
            assertTrue(e.insert("ㄋㄧˇ").accepted)
            val before=e.snapshot()
            val r=e.insert("ㄅ")
            assertEquals(EngineError.INVALID_READING,r.error)
            assertEquals(before,r.snapshot)
            assertEquals("",r.committed)
        }
    }
    @Test fun closeIsIdempotentAndRejectsLaterDispatch() {
        val e=engine(); assertTrue(e.insert("ㄋㄧˇ").accepted)
        e.close();e.close()
        assertEquals(EngineError.UNAVAILABLE,e.dispatch(EngineCommand.Commit).error)
    }
    @Test fun autoLearningIsDisabled() {
        val user=Files.createTempDirectory("chewing-no-learning").toFile()
        val e=ChewingEngine.open(File(System.getProperty("zhuoran.test.dictionary", "../vendor/dictionary")),user)
        val before=user.listFiles().orEmpty().associate { it.name to it.readBytes().toList() }
        assertTrue(e.insert("ㄋㄧˇ").accepted);assertTrue(e.insert("ㄏㄠˇ").accepted)
        e.dispatch(EngineCommand.Commit);e.close()
        val after=user.listFiles().orEmpty().associate { it.name to it.readBytes().toList() }
        assertEquals(before,after)
    }
}
