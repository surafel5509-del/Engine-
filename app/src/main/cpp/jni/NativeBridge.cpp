// JNI bridge between the Kotlin engine and the native C++ systems (script VM, landscape generator).
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include "../engine/ModelKit.h"
#include "../engine/Terrain.h"
#include "../script/SScript.h"

using namespace sengine;

namespace {

thread_local JNIEnv* tEnv = nullptr;

struct Classes {
    jclass object = nullptr, boolean = nullptr, dbl = nullptr, lng = nullptr, number = nullptr, string = nullptr, doubleArray = nullptr,
           objectArray = nullptr, nobj = nullptr, ndict = nullptr, throwable = nullptr, runtimeEx = nullptr;
    jmethodID boolValueOf = nullptr, boolValue = nullptr, dblValueOf = nullptr, lngValueOf = nullptr, numDouble = nullptr, numLong = nullptr,
              nobjInit = nullptr, ndictInit = nullptr, objToString = nullptr;
    jfieldID nobjId = nullptr, ndictKeys = nullptr, ndictValues = nullptr;
    bool ok = false;
} C;

jclass globalClass(JNIEnv* env, const char* name) {
    jclass local = env->FindClass(name);
    if (!local) { env->ExceptionClear(); return nullptr; }
    jclass g = (jclass) env->NewGlobalRef(local);
    env->DeleteLocalRef(local);
    return g;
}

bool initClasses(JNIEnv* env) {
    if (C.ok) return true;
    C.object = globalClass(env, "java/lang/Object");
    C.boolean = globalClass(env, "java/lang/Boolean");
    C.dbl = globalClass(env, "java/lang/Double");
    C.lng = globalClass(env, "java/lang/Long");
    C.number = globalClass(env, "java/lang/Number");
    C.string = globalClass(env, "java/lang/String");
    C.doubleArray = globalClass(env, "[D");
    C.objectArray = globalClass(env, "[Ljava/lang/Object;");
    C.throwable = globalClass(env, "java/lang/Throwable");
    C.runtimeEx = globalClass(env, "java/lang/RuntimeException");
    C.nobj = globalClass(env, "com/sengine/engine/script/NObj");
    C.ndict = globalClass(env, "com/sengine/engine/script/NDict");
    if (!C.object || !C.boolean || !C.dbl || !C.lng || !C.number || !C.string || !C.doubleArray || !C.nobj || !C.ndict) return false;
    C.boolValueOf = env->GetStaticMethodID(C.boolean, "valueOf", "(Z)Ljava/lang/Boolean;");
    C.boolValue = env->GetMethodID(C.boolean, "booleanValue", "()Z");
    C.dblValueOf = env->GetStaticMethodID(C.dbl, "valueOf", "(D)Ljava/lang/Double;");
    C.lngValueOf = env->GetStaticMethodID(C.lng, "valueOf", "(J)Ljava/lang/Long;");
    C.numDouble = env->GetMethodID(C.number, "doubleValue", "()D");
    C.numLong = env->GetMethodID(C.number, "longValue", "()J");
    C.objToString = env->GetMethodID(C.object, "toString", "()Ljava/lang/String;");
    C.nobjInit = env->GetMethodID(C.nobj, "<init>", "(J)V");
    C.nobjId = env->GetFieldID(C.nobj, "id", "J");
    C.ndictInit = env->GetMethodID(C.ndict, "<init>", "([Ljava/lang/String;[Ljava/lang/Object;)V");
    C.ndictKeys = env->GetFieldID(C.ndict, "keys", "[Ljava/lang/String;");
    C.ndictValues = env->GetFieldID(C.ndict, "values", "[Ljava/lang/Object;");
    C.ok = C.nobjInit && C.nobjId && C.ndictInit && C.ndictKeys && C.ndictValues;
    if (!C.ok) env->ExceptionClear();
    return C.ok;
}

std::string str(JNIEnv* env, jstring s) {
    if (!s) return "";
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

/** Java strings must be valid "modified UTF-8": replace bytes that would break NewStringUTF. */
jstring jstr(JNIEnv* env, const std::string& s) {
    std::string safe;
    safe.reserve(s.size());
    for (size_t i = 0; i < s.size(); i++) {
        unsigned char c = (unsigned char) s[i];
        if (c == 0) { safe += "\xC0\x80"; continue; }
        if (c < 0x80) { safe += (char) c; continue; }
        int len = (c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : 0;
        bool valid = len > 0 && i + len <= s.size();
        for (int k = 1; valid && k < len; k++) valid = ((unsigned char) s[i + k] >> 6) == 2;
        if (valid) { safe.append(s, i, len); i += len - 1; }
        else safe += '?';
    }
    return env->NewStringUTF(safe.c_str());
}

jobject toJava(JNIEnv* env, const Value& v) {
    switch (v.t) {
        case T::Nil: return nullptr;
        case T::Bool: return env->CallStaticObjectMethod(C.boolean, C.boolValueOf, (jboolean) v.b);
        case T::Int: return env->CallStaticObjectMethod(C.lng, C.lngValueOf, (jlong) v.i);
        case T::Float: return env->CallStaticObjectMethod(C.dbl, C.dblValueOf, (jdouble) v.f);
        case T::Str: return jstr(env, *v.s);
        case T::Vec: {
            jdoubleArray a = env->NewDoubleArray(3);
            jdouble d[3] = {v.v.x, v.v.y, v.v.z};
            env->SetDoubleArrayRegion(a, 0, 3, d);
            return a;
        }
        case T::Obj: return v.i == 0 ? nullptr : env->NewObject(C.nobj, C.nobjInit, (jlong) v.i);
        case T::List: {
            jobjectArray a = env->NewObjectArray((jsize) v.list->size(), C.object, nullptr);
            for (size_t k = 0; k < v.list->size(); k++) {
                jobject e = toJava(env, (*v.list)[k]);
                env->SetObjectArrayElement(a, (jsize) k, e);
                if (e) env->DeleteLocalRef(e);
            }
            return a;
        }
        case T::Dict: {
            jobjectArray keys = env->NewObjectArray((jsize) v.dict->keys.size(), C.string, nullptr);
            jobjectArray vals = env->NewObjectArray((jsize) v.dict->keys.size(), C.object, nullptr);
            for (size_t k = 0; k < v.dict->keys.size(); k++) {
                jstring ks = jstr(env, v.dict->keys[k]);
                env->SetObjectArrayElement(keys, (jsize) k, ks);
                env->DeleteLocalRef(ks);
                jobject e = toJava(env, v.dict->values[k]);
                env->SetObjectArrayElement(vals, (jsize) k, e);
                if (e) env->DeleteLocalRef(e);
            }
            jobject d = env->NewObject(C.ndict, C.ndictInit, keys, vals);
            env->DeleteLocalRef(keys);
            env->DeleteLocalRef(vals);
            return d;
        }
        default: return jstr(env, v.toString());
    }
}

Value fromJava(JNIEnv* env, jobject o) {
    if (!o) return Value();
    if (env->IsInstanceOf(o, C.boolean)) return Value::boolean(env->CallBooleanMethod(o, C.boolValue));
    if (env->IsInstanceOf(o, C.dbl)) return Value::number(env->CallDoubleMethod(o, C.numDouble));
    if (env->IsInstanceOf(o, C.number)) {
        jclass fl = env->FindClass("java/lang/Float");
        bool isFloat = fl && env->IsInstanceOf(o, fl);
        if (fl) env->DeleteLocalRef(fl);
        if (isFloat) return Value::number(env->CallDoubleMethod(o, C.numDouble));
        return Value::integer((int64_t) env->CallLongMethod(o, C.numLong));
    }
    if (env->IsInstanceOf(o, C.string)) return Value::str(str(env, (jstring) o));
    if (env->IsInstanceOf(o, C.nobj)) return Value::obj((int64_t) env->GetLongField(o, C.nobjId));
    if (env->IsInstanceOf(o, C.doubleArray)) {
        jdoubleArray a = (jdoubleArray) o;
        jsize n = env->GetArrayLength(a);
        jdouble d[3] = {0, 0, 0};
        env->GetDoubleArrayRegion(a, 0, n < 3 ? n : 3, d);
        return Value::vec(d[0], d[1], d[2]);
    }
    if (env->IsInstanceOf(o, C.objectArray)) {
        jobjectArray a = (jobjectArray) o;
        jsize n = env->GetArrayLength(a);
        Value l = Value::newList();
        l.list->reserve((size_t) n);
        for (jsize k = 0; k < n; k++) {
            jobject e = env->GetObjectArrayElement(a, k);
            l.list->push_back(fromJava(env, e));
            if (e) env->DeleteLocalRef(e);
        }
        return l;
    }
    if (env->IsInstanceOf(o, C.ndict)) {
        auto keys = (jobjectArray) env->GetObjectField(o, C.ndictKeys);
        auto vals = (jobjectArray) env->GetObjectField(o, C.ndictValues);
        Value d;
        d.t = T::Dict;
        d.dict = std::make_shared<Dict>();
        jsize n = keys ? env->GetArrayLength(keys) : 0;
        for (jsize k = 0; k < n; k++) {
            auto ks = (jstring) env->GetObjectArrayElement(keys, k);
            jobject e = env->GetObjectArrayElement(vals, k);
            d.dict->set(str(env, ks), fromJava(env, e));
            if (ks) env->DeleteLocalRef(ks);
            if (e) env->DeleteLocalRef(e);
        }
        if (keys) env->DeleteLocalRef(keys);
        if (vals) env->DeleteLocalRef(vals);
        return d;
    }
    auto s = (jstring) env->CallObjectMethod(o, C.objToString);
    Value r = Value::str(str(env, s));
    if (s) env->DeleteLocalRef(s);
    return r;
}

/** Converts a pending Java exception into a C++ exception with its message. */
void rethrowJava(JNIEnv* env) {
    if (!env->ExceptionCheck()) return;
    jthrowable t = env->ExceptionOccurred();
    env->ExceptionClear();
    std::string msg = "engine error";
    if (t) {
        jmethodID getMessage = env->GetMethodID(C.throwable, "getMessage", "()Ljava/lang/String;");
        auto m = getMessage ? (jstring) env->CallObjectMethod(t, getMessage) : nullptr;
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (m) { msg = str(env, m); env->DeleteLocalRef(m); }
        env->DeleteLocalRef(t);
    }
    throw std::runtime_error(msg);
}

struct JniHost : Host {
    jobject host = nullptr;
    jmethodID invoke = nullptr, logM = nullptr;

    Value call(int64_t target, const std::string& fn, std::vector<Value>& args) override {
        JNIEnv* env = tEnv;
        if (!env || !host) throw std::runtime_error("engine host not attached");
        if (env->PushLocalFrame(32 + (jint) args.size() * 4) != 0) throw std::runtime_error("JNI out of memory");
        jobjectArray a = env->NewObjectArray((jsize) args.size(), C.object, nullptr);
        for (size_t k = 0; k < args.size(); k++) env->SetObjectArrayElement(a, (jsize) k, toJava(env, args[k]));
        jstring f = jstr(env, fn);
        jobject r = env->CallObjectMethod(host, invoke, (jlong) target, f, a);
        if (env->ExceptionCheck()) {
            try { rethrowJava(env); } catch (...) { env->PopLocalFrame(nullptr); throw; }
        }
        Value v = fromJava(env, r);
        env->PopLocalFrame(nullptr);
        return v;
    }

    void log(int level, const std::string& msg) override {
        JNIEnv* env = tEnv;
        if (!env || !host) return;
        jstring m = jstr(env, msg);
        env->CallVoidMethod(host, logM, (jint) level, m);
        env->DeleteLocalRef(m);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
} gHost;

struct ProgramHandle {
    std::unique_ptr<Program> prog;
    std::unique_ptr<Interpreter> vm;
};

struct InstanceHandle {
    ProgramHandle* p;
    std::shared_ptr<Instance> inst;
};

void throwJava(JNIEnv* env, const std::string& msg) {
    if (env->ExceptionCheck()) return;
    env->ThrowNew(C.runtimeEx ? C.runtimeEx : env->FindClass("java/lang/RuntimeException"), msg.c_str());
}

std::string describe(const ScriptError& e) {
    return e.line > 0 ? "line " + std::to_string(e.line) + ": " + e.what() : std::string(e.what());
}

std::vector<Value> argsFrom(JNIEnv* env, jobjectArray args) {
    std::vector<Value> out;
    jsize n = args ? env->GetArrayLength(args) : 0;
    out.reserve((size_t) n);
    for (jsize k = 0; k < n; k++) {
        jobject e = env->GetObjectArrayElement(args, k);
        out.push_back(fromJava(env, e));
        if (e) env->DeleteLocalRef(e);
    }
    return out;
}

struct EnvScope {
    JNIEnv* saved;
    explicit EnvScope(JNIEnv* env) : saved(tEnv) { tEnv = env; }
    ~EnvScope() { tEnv = saved; }
};

TerrainParams terrainParams(JNIEnv* env, jintArray ip, jfloatArray fp) {
    TerrainParams p;
    jint i[4] = {p.resolution, p.seed, p.octaves, p.erosion};
    jfloat f[9] = {p.size, p.height, p.frequency, p.persistence, p.lacunarity, p.ridge, p.falloff, p.terraces, p.waterLevel};
    if (ip) env->GetIntArrayRegion(ip, 0, std::min<jsize>(4, env->GetArrayLength(ip)), i);
    if (fp) env->GetFloatArrayRegion(fp, 0, std::min<jsize>(9, env->GetArrayLength(fp)), f);
    p.resolution = i[0]; p.seed = i[1]; p.octaves = i[2]; p.erosion = i[3];
    p.size = f[0]; p.height = f[1]; p.frequency = f[2]; p.persistence = f[3]; p.lacunarity = f[4];
    p.ridge = f[5]; p.falloff = f[6]; p.terraces = f[7]; p.waterLevel = f[8];
    return p;
}

}  // namespace

#define JFN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_sengine_engine_script_NativeScripts_##name

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    initClasses(env);
    return JNI_VERSION_1_6;
}

JFN(jstring, nVersion)(JNIEnv* env, jclass) {
    return env->NewStringUTF("S Engine Native 1.0 — C++17 script VM, landscape generator");
}

JFN(void, nSetHost)(JNIEnv* env, jclass, jobject host) {
    initClasses(env);
    if (gHost.host) env->DeleteGlobalRef(gHost.host);
    gHost.host = host ? env->NewGlobalRef(host) : nullptr;
    if (host) {
        jclass hc = env->GetObjectClass(host);
        gHost.invoke = env->GetMethodID(hc, "invoke", "(JLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        gHost.logM = env->GetMethodID(hc, "log", "(ILjava/lang/String;)V");
        env->DeleteLocalRef(hc);
    }
}

JFN(jlong, nCompile)(JNIEnv* env, jclass, jstring name, jstring source) {
    if (!initClasses(env)) { throwJava(env, "native classes missing"); return 0; }
    try {
        auto h = new ProgramHandle();
        try {
            h->prog = compile(str(env, name), str(env, source));
        } catch (...) {
            delete h;
            throw;
        }
        h->vm = std::make_unique<Interpreter>(h->prog.get(), &gHost);
        return (jlong) (intptr_t) h;
    } catch (ScriptError& e) {
        throwJava(env, describe(e));
    } catch (std::exception& e) {
        throwJava(env, e.what());
    }
    return 0;
}

JFN(void, nFreeProgram)(JNIEnv*, jclass, jlong h) { delete (ProgramHandle*) (intptr_t) h; }

JFN(jstring, nClassName)(JNIEnv* env, jclass, jlong h) {
    auto p = (ProgramHandle*) (intptr_t) h;
    ClassDef* c = p ? p->prog->behaviourClass() : nullptr;
    return env->NewStringUTF(c ? c->name.c_str() : "");
}

JFN(jlong, nNewInstance)(JNIEnv* env, jclass, jlong h, jlong selfId) {
    auto p = (ProgramHandle*) (intptr_t) h;
    if (!p) return 0;
    EnvScope scope(env);
    try {
        ClassDef* c = p->prog->behaviourClass();
        if (!c) { throwJava(env, "no class found — declare `class MyScript : public Behaviour { … };`"); return 0; }
        auto ih = new InstanceHandle{p, p->vm->instantiate(c, (int64_t) selfId)};
        return (jlong) (intptr_t) ih;
    } catch (ScriptError& e) {
        throwJava(env, describe(e));
    } catch (std::exception& e) {
        throwJava(env, e.what());
    }
    return 0;
}

JFN(void, nFreeInstance)(JNIEnv*, jclass, jlong ih) { delete (InstanceHandle*) (intptr_t) ih; }

JFN(jboolean, nSetField)(JNIEnv* env, jclass, jlong ih, jstring name, jstring value) {
    auto i = (InstanceHandle*) (intptr_t) ih;
    return i && i->p->vm->setField(i->inst.get(), str(env, name), str(env, value));
}

JFN(jobject, nGetField)(JNIEnv* env, jclass, jlong ih, jstring name) {
    auto i = (InstanceHandle*) (intptr_t) ih;
    if (!i) return nullptr;
    return toJava(env, i->p->vm->getField(i->inst.get(), str(env, name)));
}

JFN(jobjectArray, nFields)(JNIEnv* env, jclass, jlong h) {
    auto p = (ProgramHandle*) (intptr_t) h;
    ClassDef* c = p ? p->prog->behaviourClass() : nullptr;
    size_t n = c ? c->fields.size() : 0;
    jobjectArray a = env->NewObjectArray((jsize) n, C.string, nullptr);
    for (size_t k = 0; k < n; k++) {
        jstring s = jstr(env, c->fields[k].type + " " + c->fields[k].name);
        env->SetObjectArrayElement(a, (jsize) k, s);
        env->DeleteLocalRef(s);
    }
    return a;
}

JFN(jboolean, nHas)(JNIEnv* env, jclass, jlong ih, jstring fn) {
    auto i = (InstanceHandle*) (intptr_t) ih;
    if (!i) return JNI_FALSE;
    std::string f = str(env, fn);
    if (i->p->vm->hasMethod(i->inst.get(), f)) return JNI_TRUE;
    static const std::pair<const char*, const char*> AL[] = {{"start", "BeginPlay"}, {"update", "Tick"}, {"onCollision", "OnHit"},
                                                            {"onTrigger", "OnBeginOverlap"}, {"onTriggerExit", "OnEndOverlap"}, {"onDestroy", "EndPlay"}};
    for (auto& a : AL) if (f == a.first && i->p->vm->hasMethod(i->inst.get(), a.second)) return JNI_TRUE;
    return JNI_FALSE;
}

JFN(jobject, nCall)(JNIEnv* env, jclass, jlong ih, jstring fn, jobjectArray args) {
    auto i = (InstanceHandle*) (intptr_t) ih;
    if (!i) return nullptr;
    EnvScope scope(env);
    try {
        std::vector<Value> a = argsFrom(env, args);
        Value r = i->p->vm->callMethod(i->inst, str(env, fn), a);
        return toJava(env, r);
    } catch (ScriptError& e) {
        throwJava(env, describe(e));
    } catch (std::exception& e) {
        throwJava(env, e.what());
    }
    return nullptr;
}

JFN(jobject, nCallFunction)(JNIEnv* env, jclass, jlong h, jstring fn, jobjectArray args) {
    auto p = (ProgramHandle*) (intptr_t) h;
    if (!p) return nullptr;
    EnvScope scope(env);
    try {
        std::vector<Value> a = argsFrom(env, args);
        bool found = false;
        Value r = p->vm->callFunction(str(env, fn), a, &found);
        if (!found) { throwJava(env, "function '" + str(env, fn) + "' not found"); return nullptr; }
        return toJava(env, r);
    } catch (ScriptError& e) {
        throwJava(env, describe(e));
    } catch (std::exception& e) {
        throwJava(env, e.what());
    }
    return nullptr;
}

JFN(jfloatArray, nTerrainHeights)(JNIEnv* env, jclass, jintArray ip, jfloatArray fp) {
    TerrainParams p = terrainParams(env, ip, fp);
    std::vector<float> h;
    generateHeightmap(p, h);
    jfloatArray out = env->NewFloatArray((jsize) h.size());
    env->SetFloatArrayRegion(out, 0, (jsize) h.size(), h.data());
    return out;
}

JFN(jfloatArray, nTerrainMesh)(JNIEnv* env, jclass, jintArray ip, jfloatArray fp, jfloatArray heights) {
    TerrainParams p = terrainParams(env, ip, fp);
    jsize n = env->GetArrayLength(heights);
    std::vector<float> h((size_t) n);
    env->GetFloatArrayRegion(heights, 0, n, h.data());
    std::vector<float> verts;
    std::vector<int> idx;
    buildTerrainMesh(p, h, verts, idx);
    jfloatArray out = env->NewFloatArray((jsize) verts.size());
    env->SetFloatArrayRegion(out, 0, (jsize) verts.size(), verts.data());
    return out;
}

// ================================================================================================ Model Studio kernel
namespace {

PolyMesh readMesh(JNIEnv* env, jfloatArray verts, jintArray faces, jintArray attrs, jfloatArray uvs) {
    PolyMesh m;
    jsize nv = env->GetArrayLength(verts);
    m.v.resize((size_t) nv);
    if (nv) env->GetFloatArrayRegion(verts, 0, nv, m.v.data());
    jsize nf = env->GetArrayLength(faces);
    std::vector<jint> fi((size_t) nf);
    if (nf) env->GetIntArrayRegion(faces, 0, nf, fi.data());
    const int vc = m.vertexCount();
    for (size_t i = 0; i < fi.size();) {
        int c = fi[i++];
        if (c < 0 || i + (size_t) c > fi.size()) throw std::runtime_error("corrupt face data");
        std::vector<int> f((size_t) c);
        for (int k = 0; k < c; k++) {
            f[(size_t) k] = fi[i++];
            if (f[(size_t) k] < 0 || f[(size_t) k] >= vc) throw std::runtime_error("face references a missing vertex");
        }
        m.f.push_back(f);
    }
    m.normalize();
    if (attrs) {
        jsize na = env->GetArrayLength(attrs);
        std::vector<jint> a((size_t) na);
        if (na) env->GetIntArrayRegion(attrs, 0, na, a.data());
        for (size_t i = 0; i < m.f.size() && i * 2 + 1 < a.size(); i++) { m.color[i] = a[i * 2]; m.group[i] = a[i * 2 + 1]; }
    }
    if (uvs) {
        jsize nu = env->GetArrayLength(uvs);
        std::vector<float> u((size_t) nu);
        if (nu) env->GetFloatArrayRegion(uvs, 0, nu, u.data());
        size_t o = 0;
        for (size_t i = 0; i < m.f.size(); i++) {
            size_t need = m.f[i].size() * 2;
            if (o + need > u.size()) break;
            if (!std::isnan(u[o])) m.uv[i].assign(u.begin() + (long) o, u.begin() + (long) (o + need));
            o += need;
        }
    }
    return m;
}

jobjectArray writeMesh(JNIEnv* env, PolyMesh& m, const std::vector<int>& result) {
    m.normalize();
    jclass objCls = env->FindClass("java/lang/Object");
    jobjectArray out = env->NewObjectArray(5, objCls, nullptr);
    jfloatArray v = env->NewFloatArray((jsize) m.v.size());
    if (!m.v.empty()) env->SetFloatArrayRegion(v, 0, (jsize) m.v.size(), m.v.data());
    std::vector<jint> f, a;
    std::vector<float> uv;
    for (size_t i = 0; i < m.f.size(); i++) {
        f.push_back((jint) m.f[i].size());
        for (int x : m.f[i]) f.push_back(x);
        a.push_back(m.color[i]); a.push_back(m.group[i]);
        if (m.uv[i].size() == m.f[i].size() * 2) uv.insert(uv.end(), m.uv[i].begin(), m.uv[i].end());
        else for (size_t k = 0; k < m.f[i].size() * 2; k++) uv.push_back(NAN);
    }
    jintArray fj = env->NewIntArray((jsize) f.size());
    if (!f.empty()) env->SetIntArrayRegion(fj, 0, (jsize) f.size(), f.data());
    jintArray aj = env->NewIntArray((jsize) a.size());
    if (!a.empty()) env->SetIntArrayRegion(aj, 0, (jsize) a.size(), a.data());
    jfloatArray uj = env->NewFloatArray((jsize) uv.size());
    if (!uv.empty()) env->SetFloatArrayRegion(uj, 0, (jsize) uv.size(), uv.data());
    std::vector<jint> r(result.begin(), result.end());
    jintArray rj = env->NewIntArray((jsize) r.size());
    if (!r.empty()) env->SetIntArrayRegion(rj, 0, (jsize) r.size(), r.data());
    env->SetObjectArrayElement(out, 0, v);
    env->SetObjectArrayElement(out, 1, fj);
    env->SetObjectArrayElement(out, 2, aj);
    env->SetObjectArrayElement(out, 3, uj);
    env->SetObjectArrayElement(out, 4, rj);
    return out;
}

std::vector<int> ints(JNIEnv* env, jintArray a) {
    std::vector<int> o;
    if (!a) return o;
    jsize n = env->GetArrayLength(a);
    std::vector<jint> t((size_t) n);
    if (n) env->GetIntArrayRegion(a, 0, n, t.data());
    o.assign(t.begin(), t.end());
    return o;
}
std::vector<float> floats(JNIEnv* env, jfloatArray a) {
    std::vector<float> o;
    if (!a) return o;
    jsize n = env->GetArrayLength(a);
    o.resize((size_t) n);
    if (n) env->GetFloatArrayRegion(a, 0, n, o.data());
    return o;
}

}  // namespace

/** op: 1 bevel, 2 insert edge loop, 3 bridge, 4 auto PolyGroups, 5 select PolyGroups, 6 unwrap UVs. */
JFN(jobjectArray, nMeshOp)(JNIEnv* env, jclass, jint op, jfloatArray verts, jintArray faces, jintArray attrs, jfloatArray uvs,
                           jintArray iargs, jfloatArray fargs) {
    try {
        PolyMesh m = readMesh(env, verts, faces, attrs, uvs);
        std::vector<int> ia = ints(env, iargs);
        std::vector<float> fa = floats(env, fargs);
        auto I = [&](size_t k, int d) { return k < ia.size() ? ia[k] : d; };
        auto F = [&](size_t k, float d) { return k < fa.size() ? fa[k] : d; };
        std::vector<int> result;
        switch (op) {
            case 1: {
                std::vector<int> sel(ia.begin() + std::min<size_t>(1, ia.size()), ia.end());
                result = modelkit::bevelFaces(m, sel, F(0, 0.2f), F(1, 0.1f), I(0, 3));
                break;
            }
            case 2: result.push_back(modelkit::insertEdgeLoop(m, I(0, -1), I(1, -1), F(0, 0.5f))); break;
            case 3: result = modelkit::bridgeFaces(m, I(0, -1), I(1, -1), I(2, 1)); break;
            case 4: result.push_back(modelkit::autoPolyGroups(m, F(0, 30))); break;
            case 5: result = modelkit::selectGroups(m, ia); break;
            case 6: modelkit::unwrap(m, I(0, 0), F(0, 1)); break;
            default: throw std::runtime_error("unknown mesh operation " + std::to_string(op));
        }
        return writeMesh(env, m, result);
    } catch (std::exception& e) {
        throwJava(env, e.what());
        return nullptr;
    }
}

JFN(jintArray, nRigSegment)(JNIEnv* env, jclass, jfloatArray verts, jintArray faces, jfloatArray joints, jintArray parents) {
    try {
        PolyMesh m = readMesh(env, verts, faces, nullptr, nullptr);
        std::vector<int> r = modelkit::segmentRig(m, floats(env, joints), ints(env, parents));
        std::vector<jint> o(r.begin(), r.end());
        jintArray out = env->NewIntArray((jsize) o.size());
        if (!o.empty()) env->SetIntArrayRegion(out, 0, (jsize) o.size(), o.data());
        return out;
    } catch (std::exception& e) {
        throwJava(env, e.what());
        return nullptr;
    }
}

JFN(jstring, nAutoAnimate)(JNIEnv* env, jclass, jstring kind, jobjectArray names, jintArray parents, jfloatArray rest, jfloat height, jfloat length) {
    try {
        const char* k = env->GetStringUTFChars(kind, nullptr);
        std::string ks(k ? k : "");
        if (k) env->ReleaseStringUTFChars(kind, k);
        std::vector<std::string> ns;
        jsize n = env->GetArrayLength(names);
        for (jsize i = 0; i < n; i++) {
            auto s = (jstring) env->GetObjectArrayElement(names, i);
            const char* c = s ? env->GetStringUTFChars(s, nullptr) : nullptr;
            ns.emplace_back(c ? c : "");
            if (c) env->ReleaseStringUTFChars(s, c);
            if (s) env->DeleteLocalRef(s);
        }
        std::string js = modelkit::autoAnimate(ks, ns, ints(env, parents), floats(env, rest), height, length);
        return env->NewStringUTF(js.c_str());
    } catch (std::exception& e) {
        throwJava(env, e.what());
        return nullptr;
    }
}

JFN(jstring, nAutoAnimationKinds)(JNIEnv* env, jclass) { return env->NewStringUTF(modelkit::autoAnimationKinds()); }
