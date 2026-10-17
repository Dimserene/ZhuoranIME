package tw.zhuoran.ime.engine
class UnavailableEngine:ConversionEngine {
 override fun snapshot()=EngineSnapshot()
 override fun dispatch(command:EngineCommand)=EngineReply(snapshot(),false,error=EngineError.UNAVAILABLE)
 override fun close(){}
}
