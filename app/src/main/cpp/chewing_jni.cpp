#include <jni.h>
#include <chewing.h>
#include <algorithm>
#include <cstdint>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace {
struct Context {
    ChewingContext* core;
    std::unordered_set<std::string> readings;
    bool open=false;
    int focus=0;
    explicit Context(ChewingContext* c):core(c){}
    ~Context(){chewing_delete(core);}
};
std::mutex lock;
std::unordered_map<jlong,std::unique_ptr<Context>> contexts;
jlong nextId=1;
void silentLogger(void*,int,const char*,...){}
std::string str(const char* s){return s?s:"";}
void integer(std::vector<jbyte>& out,int value){uint32_t v=static_cast<uint32_t>(value);for(int n=24;n>=0;n-=8)out.push_back(static_cast<jbyte>(v>>n));}
void text(std::vector<jbyte>& out,const std::string& s){integer(out,static_cast<int>(s.size()));out.insert(out.end(),s.begin(),s.end());}
std::vector<jbyte> snapshot(Context* ctx,bool accepted,int error,const std::string& commit=""){
    std::vector<jbyte> out;integer(out,0x5a523031);out.push_back(accepted?1:0);integer(out,error);
    integer(out,ctx?chewing_cursor_Current(ctx->core):0);
    text(out,ctx?str(chewing_buffer_String_static(ctx->core)):"");text(out,commit);
    int total=ctx&&ctx->open?chewing_cand_TotalChoice(ctx->core):0;
    int page=ctx?chewing_cand_CurrentPage(ctx->core):0;
    int count=std::max(0,std::min(10,total-page*10));
    if(count>0){out.push_back(1);integer(out,page);integer(out,chewing_cand_TotalPage(ctx->core));ctx->focus=std::clamp(ctx->focus,0,count-1);integer(out,ctx->focus);integer(out,count);
        for(int i=0;i<count;i++)text(out,str(chewing_cand_string_by_index_static(ctx->core,page*10+i)));
    }else out.push_back(0);
    return out;
}
jbyteArray bytes(JNIEnv* env,const std::vector<jbyte>& data){jbyteArray out=env->NewByteArray(static_cast<jsize>(data.size()));if(out)env->SetByteArrayRegion(out,0,static_cast<jsize>(data.size()),data.data());return out;}
std::string utf(JNIEnv* env,jstring s){const char* data=env->GetStringUTFChars(s,nullptr);if(!data)return "";std::string out(data);env->ReleaseStringUTFChars(s,data);return out;}
std::string keys(const std::string& reading){
    const std::string symbols="ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦˊˇˋ˙";
    const std::string mapping="1qaz2wsxedcrfv5tgbyhnujm8ik,9ol.0p;/-6347";
    std::unordered_map<std::string,char> table;
    size_t at=0;for(char key:mapping){size_t width=static_cast<unsigned char>(symbols[at])<0xe0?2:3;table[symbols.substr(at,width)]=key;at+=width;}
    std::string result;
    for(size_t i=0;i<reading.size();){size_t width=static_cast<unsigned char>(reading[i])<0xe0?2:3;if(i+width>reading.size())return "";auto found=table.find(reading.substr(i,width));if(found==table.end())return "";result+=found->second;i+=width;}
    if(result.empty())return "";
    if(std::string("6347").find(result.back())==std::string::npos)result+=' ';
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL Java_tw_zhuoran_ime_engine_NativeChewing_create(JNIEnv* env,jobject,jstring system,jstring user){
    std::lock_guard<std::mutex> guard(lock);
    try {
        std::string path=utf(env,system),userPath=utf(env,user);
        std::ifstream source(path+"/readings.txt");if(!source)return 0;
        std::unordered_set<std::string> readings;std::string line;while(std::getline(source,line)){if(!line.empty())readings.insert(line);}
        if(readings.size()<1000)return 0;
        auto* core=chewing_new3(path.c_str(),userPath.c_str(),"word.dat,tsi.dat,alt.dat",silentLogger,nullptr);if(!core)return 0;
        auto ctx=std::make_unique<Context>(core);ctx->readings=std::move(readings);
        chewing_set_KBType(core,KB_DEFAULT);chewing_set_candPerPage(core,10);chewing_set_maxChiSymbolLen(core,39);
        chewing_set_autoLearn(core,AUTOLEARN_DISABLED);chewing_set_escCleanAllBuf(core,0);chewing_set_spaceAsSelection(core,1);
        chewing_set_phraseChoiceRearward(core,1);chewing_set_autoShiftCur(core,1);
        chewing_config_set_int(core,"chewing.conversion_engine",CHEWING_CONVERSION_ENGINE);
        chewing_config_set_int(core,"chewing.auto_snapshot_selections",1);
        if(chewing_get_autoLearn(core)!=AUTOLEARN_DISABLED || chewing_get_candPerPage(core)!=10)return 0;
        jlong id=nextId++;contexts.emplace(id,std::move(ctx));return id;
    }catch(...){return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_tw_zhuoran_ime_engine_NativeChewing_destroy(JNIEnv*,jobject,jlong id){std::lock_guard<std::mutex> guard(lock);contexts.erase(id);}
extern "C" JNIEXPORT jbyteArray JNICALL Java_tw_zhuoran_ime_engine_NativeChewing_command(JNIEnv* env,jobject,jlong id,jint operation,jint argument,jbyteArray reading){
    std::lock_guard<std::mutex> guard(lock);
    auto it=contexts.find(id);if(it==contexts.end())return bytes(env,snapshot(nullptr,false,0));
    Context* ctx=it->second.get();auto* core=ctx->core;
    bool accepted=true;int error=-1;std::string commit;
    try {
        if(operation==1){
            jsize size=env->GetArrayLength(reading);if(size<=0||size>32)return bytes(env,snapshot(ctx,false,1));
            std::string value(static_cast<size_t>(size),'\0');env->GetByteArrayRegion(reading,0,size,reinterpret_cast<jbyte*>(value.data()));
            if(ctx->open||!ctx->readings.count(value))return bytes(env,snapshot(ctx,false,1));
            if(chewing_buffer_Len(core)>=32)return bytes(env,snapshot(ctx,false,2));
            std::string sequence=keys(value);if(sequence.empty())return bytes(env,snapshot(ctx,false,1));
            for(char key:sequence){if(key==' ')chewing_handle_Space(core);else chewing_handle_Default(core,key);}
            if(chewing_bopomofo_Check(core)||chewing_commit_Check(core)){chewing_clean_bopomofo_buf(core);accepted=false;error=3;}
        }else if(operation==2){
            if(ctx->open){accepted=false;error=3;}
            else {int amount=std::clamp(static_cast<int>(argument),-32,32);for(int i=0;i<std::abs(amount);i++){if(amount<0)chewing_handle_Left(core);else chewing_handle_Right(core);}}
        }else if(operation==3){
            if(ctx->open){accepted=false;error=3;}
            else if(chewing_cursor_Current(core)>0)chewing_handle_Backspace(core);
        }else if(operation==5){
            if(!ctx->open){accepted=false;error=3;}
            else {int count=std::min(10,chewing_cand_TotalChoice(core)-chewing_cand_CurrentPage(core)*10);ctx->focus=std::clamp(ctx->focus+std::clamp(static_cast<int>(argument),-10,10),0,std::max(0,count-1));}
        }else if(operation==6){
            if(!ctx->open){accepted=false;error=3;}
            else {int page=chewing_cand_CurrentPage(core);int target=std::clamp(page+std::clamp(static_cast<int>(argument),-10000,10000),0,std::max(0,chewing_cand_TotalPage(core)-1));
                while(page<target){chewing_handle_PageDown(core);page++;}while(page>target){chewing_handle_PageUp(core);page--;}ctx->focus=0;}
        }else if(operation==7){
            int page=chewing_cand_CurrentPage(core);int count=std::min(10,chewing_cand_TotalChoice(core)-page*10);
            if(!ctx->open||argument<0||argument>=count){accepted=false;error=3;}
            else {accepted=chewing_cand_choose_by_index(core,page*10+argument)==0;if(accepted){ctx->open=false;ctx->focus=0;}else error=3;}
        }else if(operation==4){
            if(chewing_buffer_Len(core)>0&&chewing_cursor_Current(core)==0)chewing_handle_Right(core);
            accepted=chewing_cand_open(core)==0;if(accepted)chewing_cand_list_last(core);ctx->open=accepted;ctx->focus=0;
        }else if(operation==8){if(ctx->open)chewing_cand_close(core);ctx->open=false;ctx->focus=0;
        }else if(operation==9){
            if(ctx->open){chewing_cand_close(core);ctx->open=false;}
            chewing_commit_preedit_buf(core);if(chewing_commit_Check(core))commit=str(chewing_commit_String_static(core));
        }else if(operation==10){chewing_Reset(core);ctx->open=false;ctx->focus=0;
        }else if(operation!=0){accepted=false;error=3;}
        auto reply=snapshot(ctx,accepted,error,commit);chewing_ack(core);return bytes(env,reply);
    }catch(...){return bytes(env,snapshot(ctx,false,3));}
}
