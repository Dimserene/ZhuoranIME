package tw.zhuoran.ime.engine

internal object NativeChewing {
    init { System.loadLibrary("zhuoran_chewing") }
    external fun create(systemDirectory: String, userPath: String): Long
    external fun command(handle: Long, operation: Int, argument: Int, reading: ByteArray): ByteArray
    external fun destroy(handle: Long)
}
