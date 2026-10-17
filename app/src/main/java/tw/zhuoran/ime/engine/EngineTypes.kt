package tw.zhuoran.ime.engine

data class Reading(val zhuyin: String)
data class Candidate(val id: Int, val text: String)
data class CandidatePage(val items: List<Candidate>, val page: Int, val pages: Int, val focus: Int)
data class EngineSnapshot(val preedit: String = "", val cursorCodePoint: Int = 0, val candidates: CandidatePage? = null)
data class EngineReply(val snapshot: EngineSnapshot, val accepted: Boolean, val committed: String = "", val error: EngineError? = null)
enum class EngineError { UNAVAILABLE, INVALID_READING, CAPACITY, BAD_STATE }
sealed interface EngineCommand {
    data class Insert(val reading: Reading) : EngineCommand
    data class Move(val delta: Int) : EngineCommand
    data object DeleteBefore : EngineCommand
    data object OpenCandidates : EngineCommand
    data class MoveCandidate(val delta: Int) : EngineCommand
    data class ChangePage(val delta: Int) : EngineCommand
    data class Choose(val indexOnPage: Int) : EngineCommand
    data object CloseCandidates : EngineCommand
    data object Commit : EngineCommand
    data object Clear : EngineCommand
}
interface ConversionEngine : AutoCloseable {
    fun dispatch(command: EngineCommand): EngineReply
    fun snapshot(): EngineSnapshot
    override fun close()
}
