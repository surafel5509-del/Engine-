// S Engine — C++ Script System
// A native interpreter for a C++ subset used for gameplay scripts. Scripts look and feel like
// C++ (classes deriving from Behaviour, Start/Update methods, Vec3, std::string, std::vector,
// std::cout, printf …) and run inside the engine through a tree-walking VM with resolved slots.
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>
#include <stdexcept>

namespace sengine {

struct Vec3 {
    double x = 0, y = 0, z = 0;
};

struct Value;
using List = std::vector<Value>;
struct Dict;
struct Instance;
struct Program;

enum class T : uint8_t { Nil, Bool, Int, Float, Str, Vec, Obj, List, Dict, Inst, Stream, Endl };

struct Value {
    T t = T::Nil;
    bool b = false;
    int64_t i = 0;   // Int value or object id
    double f = 0;
    Vec3 v;
    std::shared_ptr<std::string> s;
    std::shared_ptr<List> list;
    std::shared_ptr<Dict> dict;
    std::shared_ptr<Instance> inst;

    static Value nil() { return Value(); }
    static Value boolean(bool x) { Value r; r.t = T::Bool; r.b = x; return r; }
    static Value integer(int64_t x) { Value r; r.t = T::Int; r.i = x; return r; }
    static Value number(double x) { Value r; r.t = T::Float; r.f = x; return r; }
    static Value str(const std::string& x) { Value r; r.t = T::Str; r.s = std::make_shared<std::string>(x); return r; }
    static Value vec(double x, double y, double z) { Value r; r.t = T::Vec; r.v = {x, y, z}; return r; }
    static Value vec(const Vec3& a) { Value r; r.t = T::Vec; r.v = a; return r; }
    static Value obj(int64_t id) { Value r; r.t = T::Obj; r.i = id; return r; }
    static Value newList() { Value r; r.t = T::List; r.list = std::make_shared<List>(); return r; }

    bool isNum() const { return t == T::Int || t == T::Float || t == T::Bool; }
    double num() const { return t == T::Float ? f : t == T::Int ? (double) i : t == T::Bool ? (b ? 1.0 : 0.0) : 0.0; }
    int64_t toInt() const { return t == T::Int ? i : t == T::Float ? (int64_t) f : t == T::Bool ? (b ? 1 : 0) : t == T::Obj ? i : 0; }
    bool truthy() const;
    std::string toString() const;
    const char* typeName() const;
};

struct Dict {
    std::vector<std::string> keys;
    std::vector<Value> values;
    const Value* get(const std::string& k) const {
        for (size_t n = 0; n < keys.size(); n++) if (keys[n] == k) return &values[n];
        return nullptr;
    }
    void set(const std::string& k, const Value& v) {
        for (size_t n = 0; n < keys.size(); n++) if (keys[n] == k) { values[n] = v; return; }
        keys.push_back(k); values.push_back(v);
    }
};

struct ScriptError : std::runtime_error {
    int line;
    ScriptError(const std::string& m, int l) : std::runtime_error(m), line(l) {}
};

/** Engine side of the bridge: every engine API call and object property goes through here. */
struct Host {
    virtual ~Host() = default;
    /** target 0 = static API ("Input::AxisX", "Log"); otherwise a GameObject id. Properties use "get:x" / "set:x". */
    virtual Value call(int64_t target, const std::string& fn, std::vector<Value>& args) = 0;
    virtual void log(int level, const std::string& msg) = 0;
};

// ---------------------------------------------------------------- lexer
enum class Tok : uint8_t { Ident, Int, Float, Str, Char, Op, End };

struct Token {
    Tok k;
    std::string s;
    double num = 0;
    int line = 1;
};

std::vector<Token> lex(const std::string& src);

// ---------------------------------------------------------------- AST
enum class N : uint8_t {
    // expressions
    Num, Str, Bool, Nil, Ident, This, Unary, Binary, And, Or, Assign, Ternary, Call, Member, Index,
    PreInc, PostInc, Cast, New, Construct, InitList, Lambda,
    // statements
    Block, Decl, DeclGroup, If, While, DoWhile, For, ForRange, Return, Break, Continue, ExprStmt, Switch, Case, Empty
};

struct Node {
    N k;
    int line = 0;
    std::string op;        // operator / type name / member name
    int id = -1;           // interned identifier
    double num = 0;
    bool isInt = false;
    std::vector<Node*> c;  // children
    // resolution caches
    mutable const void* cacheKey = nullptr;
    mutable int cacheSlot = -1;
};

struct FuncDef {
    std::string name;
    std::string retType;
    std::vector<std::string> paramTypes;
    std::vector<int> paramIds;
    std::vector<Node*> paramDefaults;
    Node* body = nullptr;
    bool isStatic = false;
    int line = 0;
};

struct FieldDef {
    std::string name;
    int id;
    std::string type;
    Node* init = nullptr;
    bool isStatic = false;
    char kind = 'a';
};

struct ClassDef {
    std::string name;
    std::string base;
    std::vector<FieldDef> fields;
    std::unordered_map<int, int> fieldIndex;  // id -> index into fields (including inherited)
    std::unordered_map<std::string, FuncDef*> methods;
    ClassDef* baseClass = nullptr;
    bool isBehaviour = false;
    bool staticsReady = false;
    std::vector<Value> statics;
};

struct Instance {
    ClassDef* cls;
    std::vector<Value> fields;
    int64_t selfId = 0;
};

struct Slot {
    int id;
    char kind;  // 'i' int, 'f' float, 'b' bool, other = untyped
    Value v;
};

struct Program {
    std::string name;
    std::vector<std::unique_ptr<Node>> arena;
    std::vector<std::unique_ptr<FuncDef>> funcArena;
    std::vector<std::unique_ptr<ClassDef>> classArena;
    std::unordered_map<std::string, ClassDef*> classes;
    std::unordered_map<std::string, FuncDef*> functions;
    std::vector<Node*> globalDecls;
    std::vector<Slot> globals;
    bool globalsReady = false;
    std::unordered_map<std::string, int> internMap;
    std::vector<std::string> names;

    int intern(const std::string& s) {
        auto it = internMap.find(s);
        if (it != internMap.end()) return it->second;
        int id = (int) names.size();
        names.push_back(s);
        internMap[s] = id;
        return id;
    }
    Node* node(N k, int line) {
        arena.emplace_back(new Node());
        Node* n = arena.back().get();
        n->k = k; n->line = line;
        return n;
    }
    /** The class a Script component instantiates: first class deriving from Behaviour, else the first class. */
    ClassDef* behaviourClass() const;
    std::vector<std::string> classOrder;
};

/** 'i' int, 'f' float, 'b' bool, 'a' anything else — used to coerce assignments like C++ does. */
char typeKind(const std::string& normalizedType);

/** Parses [src]; throws ScriptError on syntax errors. */
std::unique_ptr<Program> compile(const std::string& name, const std::string& src);

// ---------------------------------------------------------------- interpreter
class Interpreter {
public:
    Interpreter(Program* p, Host* h) : prog(p), host(h) {}
    /** Instantiates the behaviour class for object [selfId] (runs field initialisers). */
    std::shared_ptr<Instance> instantiate(ClassDef* cls, int64_t selfId);
    bool hasMethod(Instance* inst, const std::string& fn) const;
    /** Calls a method (tries the exact name, then Capitalised: "update" -> "Update"). Returns Nil if missing. */
    Value callMethod(const std::shared_ptr<Instance>& inst, const std::string& fn, std::vector<Value>& args, bool* found = nullptr);
    Value callFunction(const std::string& fn, std::vector<Value>& args, bool* found = nullptr);
    bool setField(Instance* inst, const std::string& name, const std::string& value);
    Value getField(Instance* inst, const std::string& name);
    void ensureGlobals();

    long budgetPerCall = 20000000;  // runaway-loop guard (statements + calls per top level call)

private:
    enum class Flow { Normal, Break, Continue, Return };
    struct Frame { size_t base; std::shared_ptr<Instance> self; };

    Program* prog;
    Host* host;
    std::vector<Slot> stack;
    std::vector<Frame> frames;
    Value retVal;
    long budget = 0;
    int depth = 0;
    std::string coutBuf;

    Flow exec(Node* n);
    Value eval(Node* n);
    Value evalCall(Node* n);
    Value invoke(FuncDef* f, const std::shared_ptr<Instance>& self, std::vector<Value>& args, int line);
    Value builtin(const std::string& name, std::vector<Value>& args, int line, bool& ok);
    Value memberGet(const Value& base, const std::string& name, int line);
    Value methodCall(Value& base, Node* baseNode, const std::string& name, std::vector<Value>& args, int line);
    void assign(Node* target, const Value& v);
    Value* lookup(Node* ident, char& kind);
    Value declValue(const std::string& type, const Value& v, bool copyLists, int line);
    FuncDef* findMethod(ClassDef* c, const std::string& name) const;
    void initStatics(ClassDef* c);
    bool equals(const Value& a, const Value& b) const;
    Value evalIdentFallback(Node* n);
    Value binary(const std::string& op, const Value& a, const Value& b, int line);
    Value construct(const std::string& type, std::vector<Value>& args, int line);
    Value defaultFor(const std::string& type, int line);
    Value coerce(char kind, const Value& v);
    static char kindOf(const std::string& type) { return typeKind(type); }
    void tick(int line);
    void flushCout(bool force);
    Value hostCall(int64_t target, const std::string& fn, std::vector<Value>& args, int line);
    Instance* self() { return frames.empty() ? nullptr : frames.back().self.get(); }
    [[noreturn]] void fail(const std::string& msg, int line) { throw ScriptError(msg, line); }
};

}  // namespace sengine
