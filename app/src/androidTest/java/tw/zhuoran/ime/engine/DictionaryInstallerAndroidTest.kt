package tw.zhuoran.ime.engine
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class DictionaryInstallerAndroidTest {
    private val source get():File {
        val c=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        return DictionaryInstaller(File(c.filesDir,"installer-test-source")).install {c.assets.open("chewing/$it")}
    }
    @Test fun damagedDictionaryRetainsPreviousVersion() {
        val root=Files.createTempDirectory("dictionary").toFile()
        val installer=DictionaryInstaller(root)
        val first=installer.install { File(source,it).inputStream() }
        val corrupt=Files.createTempDirectory("corrupt").toFile();source.copyRecursively(corrupt,true)
        File(corrupt,"dictionary.manifest").appendText("\nversion=another\n")
        File(corrupt,"word.dat").appendText("broken")
        try {installer.install {File(corrupt,it).inputStream()};fail("must reject")} catch(_:Exception){}
        assertEquals(first,installer.active())
        DictionaryManifest.read(first).verify(first)
    }
    @Test fun interruptedInstallNeverPromotesPartialDirectory() {
        val root=Files.createTempDirectory("dictionary").toFile();val installer=DictionaryInstaller(root)
        try {installer.install {if(it=="tsi.dat")error("interrupted") else File(source,it).inputStream()};fail("must reject")}catch(_:Exception){}
        assertNull(installer.active())
        assertTrue(root.listFiles().orEmpty().none {it.name.startsWith("stage-")})
    }
}
