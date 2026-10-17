package tw.zhuoran.ime.engine

import java.io.File
import java.security.MessageDigest
import java.util.Properties

internal data class DictionaryManifest(val version: String, val hashes: Map<String,String>) {
    companion object {
        val files=listOf("word.dat","tsi.dat","alt.dat","readings.txt")
        fun read(directory: File): DictionaryManifest {
            val props=Properties().apply { File(directory,"dictionary.manifest").reader(Charsets.UTF_8).use { load(it) } }
            val version=props.getProperty("version") ?: error("Missing dictionary version")
            require(version.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,99}")) && !version.contains(".."))
            val hashes=files.associateWith { name ->
                (props.getProperty("sha256.$name") ?: error("Missing dictionary checksum")).also {
                    require(it.matches(Regex("[a-f0-9]{64}")))
                }
            }
            return DictionaryManifest(version,hashes)
        }
        fun sha256(file: File): String {
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer=ByteArray(8192)
                while(true) {val count=stream.read(buffer);if(count<0)break;digest.update(buffer,0,count)}
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
    fun verify(directory: File) {
        hashes.forEach { (name,hash) ->
            val file=File(directory,name)
            require(file.isFile && file.length()>0 && file.canonicalFile.parentFile==directory.canonicalFile)
            require(sha256(file)==hash) { "Dictionary integrity failure" }
        }
        require(File(directory,"word.dat").length()>100_000 && File(directory,"tsi.dat").length()>1_000_000) { "Full dictionary required" }
    }
}
