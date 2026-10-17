package tw.zhuoran.ime.engine

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File

class ChewingEngine private constructor(private var handle: Long) : ConversionEngine {
    private var last = EngineSnapshot()
    companion object {
        fun open(systemDirectory: File, userDirectory: File): ChewingEngine {
            DictionaryManifest.read(systemDirectory).verify(systemDirectory)
            check(userDirectory.isDirectory || userDirectory.mkdirs()) { "User storage unavailable" }
            val handle = NativeChewing.create(systemDirectory.absolutePath, File(userDirectory,"user.dat").absolutePath)
            check(handle != 0L) { "Chinese engine unavailable" }
            return ChewingEngine(handle)
        }
    }
    @Synchronized override fun snapshot(): EngineSnapshot = last
    @Synchronized override fun dispatch(command: EngineCommand): EngineReply {
        if(handle == 0L) return EngineReply(EngineSnapshot(),false,error=EngineError.UNAVAILABLE)
        var argument=0
        var reading=byteArrayOf()
        val operation=when(command) {
            is EngineCommand.Insert -> {reading=command.reading.zhuyin.toByteArray(Charsets.UTF_8);1}
            is EngineCommand.Move -> {argument=command.delta;2}
            EngineCommand.DeleteBefore -> 3
            EngineCommand.OpenCandidates -> 4
            is EngineCommand.MoveCandidate -> {argument=command.delta;5}
            is EngineCommand.ChangePage -> {argument=command.delta;6}
            is EngineCommand.Choose -> {argument=command.indexOnPage;7}
            EngineCommand.CloseCandidates -> 8
            EngineCommand.Commit -> 9
            EngineCommand.Clear -> 10
        }
        return try {
            decode(NativeChewing.command(handle,operation,argument,reading)).also { last=it.snapshot }
        } catch (_: Exception) {
            close()
            EngineReply(last,false,error=EngineError.BAD_STATE)
        }
    }
    @Synchronized override fun close() {
        if(handle!=0L) {NativeChewing.destroy(handle);handle=0}
    }
    private fun decode(bytes: ByteArray): EngineReply = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt()==0x5a523031)
        val accepted=input.readBoolean()
        val error=input.readInt().let { if(it<0)null else EngineError.entries[it] }
        val cursor=input.readInt()
        fun text():String {
            val size=input.readInt();require(size in 0..1_048_576)
            val data=ByteArray(size);input.readFully(data)
            return data.toString(Charsets.UTF_8)
        }
        val preedit=text(); val committed=text()
        require(cursor in 0..preedit.codePointCount(0,preedit.length))
        val page=if(input.readBoolean()) {
            val index=input.readInt();val pages=input.readInt();val focus=input.readInt();val count=input.readInt()
            require(pages>0 && index in 0 until pages && count in 1..10 && focus in 0 until count)
            CandidatePage(List(count) { Candidate(index*10+it,text()) },index,pages,focus)
        } else null
        require(input.available()==0)
        EngineReply(EngineSnapshot(preedit,cursor,page),accepted,committed,error)
    }
}
