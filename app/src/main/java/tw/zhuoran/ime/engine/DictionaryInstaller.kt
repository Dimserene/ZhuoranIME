package tw.zhuoran.ime.engine

import java.io.File
import java.io.InputStream
import java.util.UUID

/** Install to a fresh directory; verified content is promoted before the pointer changes. */
class DictionaryInstaller(private val root: File) {
    @Synchronized fun active(): File? = try {
        val name=File(root,"active").readText().trim()
        require(name.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,99}")) && !name.contains(".."))
        File(root,name).also {DictionaryManifest.read(it).verify(it)}
    } catch(_:Exception) {null}

    @Synchronized fun install(open: (String)->InputStream): File {
        check(root.isDirectory || root.mkdirs())
        val stage=File(root,"stage-${UUID.randomUUID()}");check(stage.mkdir())
        try {
            fun copy(name:String) {open(name).use {input->File(stage,name).outputStream().use {output->input.copyTo(output);output.fd.sync()}}}
            copy("dictionary.manifest")
            val manifest=DictionaryManifest.read(stage)
            DictionaryManifest.files.forEach(::copy)
            manifest.verify(stage)
            val destination=File(root,manifest.version)
            if(destination.exists()) {
                manifest.verify(destination)
                require(File(stage,"dictionary.manifest").readBytes().contentEquals(File(destination,"dictionary.manifest").readBytes()))
            } else check(stage.renameTo(destination)) {"Dictionary promotion failed"}
            val pointer=File(root,"active-${UUID.randomUUID()}")
            try {
                pointer.outputStream().use {it.write(manifest.version.toByteArray());it.fd.sync()}
                check(pointer.renameTo(File(root,"active"))) {"Dictionary activation failed"}
            } finally {pointer.delete()}
            return destination
        } finally {stage.deleteRecursively()}
    }
}
