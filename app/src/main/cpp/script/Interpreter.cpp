#include "SScript.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstring>

namespace sengine {

// ================================================================ Value
bool Value::truthy() const {
    switch (t) {
        case T::Nil: return false;
        case T::Bool: return b;
        case T::Int: return i != 0;
        case T::Float: return f != 0.0;
        case T::Obj: return i != 0;
        default: return true;
    }
}

static std::string fmtG(double d) {
    if (std::isnan(d)) return "nan";
    if (std::isinf(d)) return d > 0 ? "inf" : "-inf";
    char buf[64];
    snprintf(buf, sizeof buf, "%g", d);
    return buf;
}

std::string Value::toString() const {
    switch (t) {
        case T::Nil: return "nullptr";
        case T::Bool: return b ? "true" : "false";
        case T::Int: return std::to_string(i);
        case T::Float: return fmtG(f);
        case T::Str: return *s;
        case T::Vec: return "(" + fmtG(v.x) + ", " + fmtG(v.y) + ", " + fmtG(v.z) + ")";
        case T::Obj: return i == 0 ? "nullptr" : "GameObject#" + std::to_string(i);
        case T::List: {
            std::string r = "[";
            for (size_t k = 0; k < list->size(); k++) { if (k) r += ", "; r += (*list)[k].toString(); }
            return r + "]";
        }
        case T::Dict: {
            std::string r = "{";
            for (size_t k = 0; k < dict->keys.size(); k++) { if (k) r += ", "; r += dict->keys[k] + ": " + dict->values[k].toString(); }
            return r + "}";
        }
        case T::Inst: return "<" + inst->cls->name + ">";
        case T::Stream: return "<stream>";
        case T::Endl: return "\n";
    }
    return "";
}

const char* Value::typeName() const {
    switch (t) {
        case T::Nil: return "nullptr";
        case T::Bool: return "bool";
        case T::Int: return "int";
        case T::Float: return "float";
        case T::Str: return "string";
        case T::Vec: return "Vec3";
        case T::Obj: return "GameObject";
        case T::List: return "vector";
        case T::Dict: return "map";
        case T::Inst: return "object";
        case T::Stream: return "ostream";
        case T::Endl: return "endl";
    }
    return "?";
}

// ================================================================ helpers
static uint64_t rngState = 0x9E3779B97F4A7C15ull;
static double rnd01() {
    rngState ^= rngState << 13; rngState ^= rngState >> 7; rngState ^= rngState << 17;
    return (double) (rngState >> 11) * (1.0 / 9007199254740992.0);
}

static std::string lower(const std::string& s) {
    std::string r = s;
    for (char& c : r) c = (char) tolower((unsigned char) c);
    return r;
}

static std::string formatPrintf(const std::string& fmt, const std::vector<Value>& args, size_t start) {
    std::string out;
    size_t ai = start;
    for (size_t i = 0; i < fmt.size(); i++) {
        char c = fmt[i];
        if (c != '%') { out += c; continue; }
        if (i + 1 < fmt.size() && fmt[i + 1] == '%') { out += '%'; i++; continue; }
        size_t j = i + 1;
        std::string spec = "%";
        while (j < fmt.size() && strchr("-+ #0123456789.*", fmt[j])) spec += fmt[j++];
        while (j < fmt.size() && strchr("hlLqjzt", fmt[j])) j++;  // length modifiers are irrelevant here
        if (j >= fmt.size()) { out += spec; break; }
        char conv = fmt[j];
        Value a = ai < args.size() ? args[ai] : Value();
        ai++;
        char buf[512];
        switch (conv) {
            case 'd': case 'i': case 'u': case 'c':
                if (conv == 'c' && a.t == T::Str) { out += *a.s; break; }
                snprintf(buf, sizeof buf, (spec + (conv == 'c' ? "c" : "lld")).c_str(), conv == 'c' ? (int) a.toInt() : (long long) a.toInt());
                out += buf;
                break;
            case 'x': case 'X': case 'o':
                snprintf(buf, sizeof buf, (spec + "ll" + conv).c_str(), (long long) a.toInt());
                out += buf;
                break;
            case 'f': case 'F': case 'g': case 'G': case 'e': case 'E':
                snprintf(buf, sizeof buf, (spec + conv).c_str(), a.num());
                out += buf;
                break;
            case 's': {
                std::string s = a.toString();
                snprintf(buf, sizeof buf, (spec + "s").c_str(), s.c_str());
                out += buf;
                break;
            }
            default: out += spec + conv; ai--;
        }
        i = j;
    }
    return out;
}

// ================================================================ interpreter core
FuncDef* Interpreter::findMethod(ClassDef* c, const std::string& name) const {
    for (ClassDef* k = c; k; k = k->baseClass) {
        auto it = k->methods.find(name);
        if (it != k->methods.end()) return it->second;
    }
    return nullptr;
}

void Interpreter::tick(int line) {
    if (--budget < 0) fail("script ran too long (infinite loop?)", line);
}

void Interpreter::flushCout(bool force) {
    size_t nl;
    while ((nl = coutBuf.find('\n')) != std::string::npos) {
        if (host) host->log(0, coutBuf.substr(0, nl));
        coutBuf.erase(0, nl + 1);
    }
    if (force && !coutBuf.empty()) { if (host) host->log(0, coutBuf); coutBuf.clear(); }
}

Value Interpreter::hostCall(int64_t target, const std::string& fn, std::vector<Value>& args, int line) {
    if (!host) fail("no engine attached", line);
    try {
        return host->call(target, fn, args);
    } catch (ScriptError&) {
        throw;
    } catch (std::exception& e) {
        fail(e.what(), line);
    }
}

Value Interpreter::coerce(char kind, const Value& v) {
    switch (kind) {
        case 'i': return v.isNum() ? Value::integer(v.toInt()) : v;
        case 'f': return v.isNum() ? Value::number(v.num()) : v;
        case 'b': return v.t == T::Str ? v : Value::boolean(v.truthy());
        default: return v;
    }
}

Value Interpreter::defaultFor(const std::string& type, int line) {
    if (type == "int") return Value::integer(0);
    if (type == "float") return Value::number(0);
    if (type == "bool") return Value::boolean(false);
    if (type == "string") return Value::str("");
    if (type == "Vec3") return Value::vec(0, 0, 0);
    if (type == "List") return Value::newList();
    if (type == "Dict") { Value r; r.t = T::Dict; r.dict = std::make_shared<Dict>(); return r; }
    auto it = prog->classes.find(type);
    if (it != prog->classes.end()) { std::vector<Value> none; return construct(type, none, line); }
    return Value();
}

Value Interpreter::declValue(const std::string& type, const Value& v, bool copyLists, int line) {
    if (type == "Vec3" && v.t == T::List) {
        const List& l = *v.list;
        return Value::vec(l.size() > 0 ? l[0].num() : 0, l.size() > 1 ? l[1].num() : 0, l.size() > 2 ? l[2].num() : 0);
    }
    if (type == "List" && v.t == T::List && copyLists) {
        Value r = Value::newList();
        *r.list = *v.list;
        return r;
    }
    if (type == "string" && v.t == T::Nil) return Value::str("");
    if (v.t == T::List && prog->classes.count(type)) {
        std::vector<Value> args = *v.list;
        return construct(type, args, line);
    }
    return coerce(typeKind(type), v);
}

void Interpreter::initStatics(ClassDef* c) {
    if (c->staticsReady) return;
    c->staticsReady = true;
    for (size_t k = 0; k < c->fields.size(); k++) {
        FieldDef& fd = c->fields[k];
        if (!fd.isStatic) continue;
        c->statics[k] = fd.init ? declValue(fd.type, eval(fd.init), true, 0) : defaultFor(fd.type, 0);
    }
}

void Interpreter::ensureGlobals() {
    if (prog->globalsReady) return;
    if (depth == 0 && frames.empty()) budget = budgetPerCall;
    prog->globalsReady = true;
    for (Node* d : prog->globalDecls) {
        Value v = d->c.empty() ? defaultFor(d->op, d->line) : declValue(d->op, eval(d->c[0]), true, d->line);
        prog->globals.push_back({d->id, typeKind(d->op), v});
    }
}

std::shared_ptr<Instance> Interpreter::instantiate(ClassDef* cls, int64_t selfId) {
    if (depth == 0 && frames.empty()) budget = budgetPerCall;
    ensureGlobals();
    initStatics(cls);
    auto inst = std::make_shared<Instance>();
    inst->cls = cls;
    inst->selfId = selfId;
    inst->fields.resize(cls->fields.size());
    frames.push_back({stack.size(), inst});
    try {
        for (size_t k = 0; k < cls->fields.size(); k++) {
            FieldDef& fd = cls->fields[k];
            if (fd.isStatic) continue;
            if (!fd.init) { inst->fields[k] = defaultFor(fd.type, 0); continue; }
            Node* init = fd.init;
            Value v;
            if (init->k == N::Construct && !init->isInt && init->op != "List") {
                std::vector<Value> args;
                for (Node* a : init->c) args.push_back(eval(a));
                v = construct(fd.type, args, init->line);
            } else v = declValue(fd.type, eval(init), true, init->line);
            inst->fields[k] = v;
        }
    } catch (...) {
        frames.pop_back();
        throw;
    }
    frames.pop_back();
    return inst;
}

Value Interpreter::construct(const std::string& typeIn, std::vector<Value>& args, int line) {
    std::string type = typeIn;
    if (type == "Vector3" || type == "FVector" || type == "vec3" || type == "Vec2" || type == "Vector2") type = "Vec3";
    if (type == "double") type = "float";
    if (type == "std::string" || type == "FString") type = "string";
    if (type == "Vec3") {
        if (args.empty()) return Value::vec(0, 0, 0);
        if (args.size() == 1) return args[0].t == T::Vec ? args[0] : Value::vec(args[0].num(), args[0].num(), args[0].num());
        return Value::vec(args[0].num(), args[1].num(), args.size() > 2 ? args[2].num() : 0);
    }
    if (type == "string") {
        if (args.empty()) return Value::str("");
        if (args.size() == 2 && args[0].isNum()) {
            std::string r;
            std::string piece = args[1].toString();
            for (int64_t k = 0; k < args[0].toInt(); k++) r += piece;
            return Value::str(r);
        }
        return Value::str(args[0].toString());
    }
    if (type == "int") return Value::integer(args.empty() ? 0 : args[0].toInt());
    if (type == "float") return Value::number(args.empty() ? 0 : args[0].num());
    if (type == "bool") return Value::boolean(!args.empty() && args[0].truthy());
    if (type == "GameObject") return args.empty() ? Value() : args[0];
    if (type == "List") {
        Value r = Value::newList();
        if (!args.empty()) {
            int64_t n = args[0].toInt();
            if (n < 0 || n > 10000000) fail("bad vector size", line);
            r.list->assign((size_t) n, args.size() > 1 ? args[1] : Value::integer(0));
        }
        return r;
    }
    if (type == "Dict") return defaultFor("Dict", line);
    auto it = prog->classes.find(type);
    if (it == prog->classes.end()) fail("unknown type '" + typeIn + "'", line);
    auto inst = instantiate(it->second, self() ? self()->selfId : 0);
    if (FuncDef* ctor = findMethod(it->second, "__ctor")) invoke(ctor, inst, args, line);
    Value r;
    r.t = T::Inst;
    r.inst = inst;
    return r;
}

Value Interpreter::invoke(FuncDef* f, const std::shared_ptr<Instance>& selfp, std::vector<Value>& args, int line) {
    if (depth > 200) fail("stack overflow (recursion too deep) in " + f->name, line);
    if (!f->body) fail("function '" + f->name + "' has no body", line);
    tick(line);
    size_t base = stack.size();
    for (size_t k = 0; k < f->paramIds.size(); k++) {
        Value v;
        if (k < args.size()) v = args[k];
        else if (f->paramDefaults[k]) v = eval(f->paramDefaults[k]);
        else {
            stack.resize(base);
            fail("too few arguments to '" + f->name + "' (expected " + std::to_string(f->paramIds.size()) + ")", line);
        }
        stack.push_back({f->paramIds[k], typeKind(f->paramTypes[k]), declValue(f->paramTypes[k], v, false, line)});
    }
    frames.push_back({base, selfp});
    depth++;
    Flow fl;
    try {
        fl = exec(f->body);
    } catch (...) {
        frames.pop_back();
        depth--;
        stack.resize(base);
        throw;
    }
    frames.pop_back();
    depth--;
    stack.resize(base);
    Value r = fl == Flow::Return ? retVal : Value();
    retVal = Value();
    if (f->retType == "void") return Value();
    return declValue(f->retType, r, false, line);
}

bool Interpreter::hasMethod(Instance* inst, const std::string& fn) const {
    if (!inst) return false;
    std::string cap = fn;
    if (!cap.empty()) cap[0] = (char) toupper((unsigned char) cap[0]);
    return findMethod(inst->cls, fn) || findMethod(inst->cls, cap);
}

static std::vector<std::string> aliasesFor(const std::string& fn) {
    std::vector<std::string> r = {fn};
    std::string cap = fn;
    if (!cap.empty()) cap[0] = (char) toupper((unsigned char) cap[0]);
    if (cap != fn) r.push_back(cap);
    if (fn == "start") { r.push_back("BeginPlay"); r.push_back("Awake"); r.push_back("OnStart"); }
    else if (fn == "update") { r.push_back("Tick"); r.push_back("OnUpdate"); }
    else if (fn == "onCollision") { r.push_back("OnCollisionEnter"); r.push_back("OnHit"); }
    else if (fn == "onTrigger") { r.push_back("OnTriggerEnter"); r.push_back("OnBeginOverlap"); }
    else if (fn == "onTriggerExit") { r.push_back("OnEndOverlap"); }
    else if (fn == "onDestroy") { r.push_back("EndPlay"); r.push_back("OnDestroyed"); }
    else if (fn == "onTap") { r.push_back("OnClicked"); r.push_back("OnTouch"); }
    else if (fn == "onStop") { r.push_back("OnApplicationQuit"); }
    return r;
}

Value Interpreter::callMethod(const std::shared_ptr<Instance>& inst, const std::string& fn, std::vector<Value>& args, bool* found) {
    FuncDef* f = nullptr;
    for (const std::string& n : aliasesFor(fn)) if ((f = findMethod(inst->cls, n))) break;
    if (found) *found = f != nullptr;
    if (!f) return Value();
    if (depth == 0) budget = budgetPerCall;
    // lifecycle functions may take fewer parameters than the engine passes (e.g. Update() without dt)
    std::vector<Value> a(args.begin(), args.begin() + std::min(args.size(), f->paramIds.size()));
    Value r;
    try {
        r = invoke(f, inst, a, f->line);
    } catch (...) {
        flushCout(true);
        throw;
    }
    if (depth == 0) flushCout(true);
    return r;
}

Value Interpreter::callFunction(const std::string& fn, std::vector<Value>& args, bool* found) {
    ensureGlobals();
    auto it = prog->functions.find(fn);
    if (found) *found = it != prog->functions.end();
    if (it == prog->functions.end()) return Value();
    if (depth == 0) budget = budgetPerCall;
    Value r = invoke(it->second, nullptr, args, it->second->line);
    if (depth == 0) flushCout(true);
    return r;
}

bool Interpreter::setField(Instance* inst, const std::string& name, const std::string& value) {
    auto nit = prog->internMap.find(name);
    if (nit == prog->internMap.end()) return false;
    auto it = inst->cls->fieldIndex.find(nit->second);
    if (it == inst->cls->fieldIndex.end()) return false;
    FieldDef& fd = inst->cls->fields[it->second];
    Value& slot = fd.isStatic ? inst->cls->statics[it->second] : inst->fields[it->second];
    std::string v = value;
    if (fd.type == "int") slot = Value::integer((int64_t) strtod(v.c_str(), nullptr));
    else if (fd.type == "float") slot = Value::number(strtod(v.c_str(), nullptr));
    else if (fd.type == "bool") slot = Value::boolean(v == "true" || v == "1" || v == "yes");
    else if (fd.type == "Vec3") {
        double c[3] = {0, 0, 0};
        int n = 0;
        const char* p = v.c_str();
        while (*p && n < 3) {
            while (*p && !(isdigit((unsigned char) *p) || *p == '-' || *p == '.')) p++;
            if (!*p) break;
            char* end;
            c[n++] = strtod(p, &end);
            p = end;
        }
        slot = Value::vec(c[0], c[1], c[2]);
    } else slot = Value::str(v);
    return true;
}

Value Interpreter::getField(Instance* inst, const std::string& name) {
    auto nit = prog->internMap.find(name);
    if (nit == prog->internMap.end()) return Value();
    auto it = inst->cls->fieldIndex.find(nit->second);
    if (it == inst->cls->fieldIndex.end()) return Value();
    return inst->cls->fields[it->second].isStatic ? inst->cls->statics[it->second] : inst->fields[it->second];
}

Value* Interpreter::lookup(Node* n, char& kind) {
    int id = n->id;
    size_t base = frames.empty() ? 0 : frames.back().base;
    for (size_t k = stack.size(); k > base; k--) {
        Slot& s = stack[k - 1];
        if (s.id == id) { kind = s.kind; return &s.v; }
    }
    if (!frames.empty() && frames.back().self) {
        Instance* in = frames.back().self.get();
        auto it = in->cls->fieldIndex.find(id);
        if (it != in->cls->fieldIndex.end()) {
            FieldDef& fd = in->cls->fields[it->second];
            kind = fd.kind;
            return fd.isStatic ? &in->cls->statics[it->second] : &in->fields[it->second];
        }
    }
    for (Slot& s : prog->globals) if (s.id == id) { kind = s.kind; return &s.v; }
    return nullptr;
}

// ================================================================ statements
Interpreter::Flow Interpreter::exec(Node* n) {
    tick(n->line);
    switch (n->k) {
        case N::Block: {
            size_t mark = stack.size();
            for (Node* s : n->c) {
                Flow f = exec(s);
                if (f != Flow::Normal) { stack.resize(mark); return f; }
            }
            stack.resize(mark);
            return Flow::Normal;
        }
        case N::Decl: {
            Value v;
            if (n->c.empty()) v = defaultFor(n->op, n->line);
            else {
                Node* init = n->c[0];
                if (init->k == N::Construct && init->op.rfind("__array:", 0) == 0) {
                    int64_t size = eval(init->c[0]).toInt();
                    if (size < 0 || size > 10000000) fail("bad array size", n->line);
                    v = Value::newList();
                    v.list->assign((size_t) size, defaultFor(init->op.substr(8), n->line));
                } else if (init->k == N::Construct && !init->isInt && init->op != "List") {
                    std::vector<Value> args;
                    for (Node* a : init->c) args.push_back(eval(a));
                    v = construct(n->op, args, n->line);
                } else v = declValue(n->op, eval(init), init->k == N::Ident, n->line);
            }
            stack.push_back({n->id, typeKind(n->op), v});
            return Flow::Normal;
        }
        case N::DeclGroup:
            for (Node* d : n->c) exec(d);
            return Flow::Normal;
        case N::ExprStmt:
            eval(n->c[0]);
            return Flow::Normal;
        case N::If: {
            size_t mark = stack.size();
            bool cond = eval(n->c[0]).truthy();
            Flow f = Flow::Normal;
            if (cond) f = exec(n->c[1]);
            else if (n->c.size() > 2) f = exec(n->c[2]);
            stack.resize(mark);
            return f;
        }
        case N::While: {
            size_t mark = stack.size();
            while (true) {
                stack.resize(mark);
                tick(n->line);
                if (!eval(n->c[0]).truthy()) break;
                Flow f = exec(n->c[1]);
                if (f == Flow::Break) break;
                if (f == Flow::Return) { stack.resize(mark); return f; }
            }
            stack.resize(mark);
            return Flow::Normal;
        }
        case N::DoWhile: {
            while (true) {
                tick(n->line);
                Flow f = exec(n->c[0]);
                if (f == Flow::Break) break;
                if (f == Flow::Return) return f;
                if (!eval(n->c[1]).truthy()) break;
            }
            return Flow::Normal;
        }
        case N::For: {
            size_t mark = stack.size();
            if (n->c[0]->k != N::Empty) exec(n->c[0]);
            while (true) {
                tick(n->line);
                if (n->c[1]->k != N::Empty && !eval(n->c[1]).truthy()) break;
                Flow f = exec(n->c[3]);
                if (f == Flow::Break) break;
                if (f == Flow::Return) { stack.resize(mark); return f; }
                if (n->c[2]->k != N::Empty) eval(n->c[2]);
            }
            stack.resize(mark);
            return Flow::Normal;
        }
        case N::ForRange: {
            Value range = eval(n->c[0]);
            size_t mark = stack.size();
            std::vector<Value> items;
            if (range.t == T::List) items = *range.list;  // iterate a snapshot: the body may modify the vector
            else if (range.t == T::Str) for (char ch : *range.s) items.push_back(Value::str(std::string(1, ch)));
            else if (range.t == T::Dict) for (auto& k : range.dict->keys) items.push_back(Value::str(k));
            else fail(std::string("cannot iterate over ") + range.typeName(), n->line);
            char kind = typeKind(n->op);
            for (Value& it : items) {
                stack.resize(mark);
                stack.push_back({n->id, kind, coerce(kind, it)});
                Flow f = exec(n->c[1]);
                if (f == Flow::Break) break;
                if (f == Flow::Return) { stack.resize(mark); return f; }
            }
            stack.resize(mark);
            return Flow::Normal;
        }
        case N::Return:
            retVal = n->c.empty() ? Value() : eval(n->c[0]);
            return Flow::Return;
        case N::Break: return Flow::Break;
        case N::Continue: return Flow::Continue;
        case N::Empty: return Flow::Normal;
        case N::Switch: {
            Value v = eval(n->c[0]);
            size_t start = 0;
            for (size_t k = 1; k < n->c.size() && !start; k++)
                if (n->c[k]->op != "default" && equals(v, eval(n->c[k]->c[0]))) start = k;
            if (!start) for (size_t k = 1; k < n->c.size(); k++) if (n->c[k]->op == "default") { start = k; break; }
            if (!start) return Flow::Normal;
            size_t mark = stack.size();
            for (size_t k = start; k < n->c.size(); k++) {
                Node* cs = n->c[k];
                for (size_t s = 1; s < cs->c.size(); s++) {
                    Flow f = exec(cs->c[s]);
                    if (f == Flow::Break) { stack.resize(mark); return Flow::Normal; }
                    if (f != Flow::Normal) { stack.resize(mark); return f; }
                }
            }
            stack.resize(mark);
            return Flow::Normal;
        }
        default:
            eval(n);
            return Flow::Normal;
    }
}

// ================================================================ expressions
bool Interpreter::equals(const Value& a, const Value& b) const {
    if (a.isNum() && b.isNum()) {
        if (a.t == T::Int && b.t == T::Int) return a.i == b.i;
        return a.num() == b.num();
    }
    if (a.t == T::Str && b.t == T::Str) return *a.s == *b.s;
    if (a.t == T::Vec && b.t == T::Vec) return a.v.x == b.v.x && a.v.y == b.v.y && a.v.z == b.v.z;
    bool aNull = a.t == T::Nil || (a.t == T::Obj && a.i == 0);
    bool bNull = b.t == T::Nil || (b.t == T::Obj && b.i == 0);
    if (aNull || bNull) return aNull && bNull;
    if (a.t == T::Obj && b.t == T::Obj) return a.i == b.i;
    if (a.t == T::Inst && b.t == T::Inst) return a.inst == b.inst;
    if (a.t == T::List && b.t == T::List) return a.list == b.list;
    return false;
}

Value Interpreter::binary(const std::string& op, const Value& a, const Value& b, int line) {
    if (a.t == T::Stream && op == "<<") {
        coutBuf += b.t == T::Endl ? std::string("\n") : b.toString();
        flushCout(false);
        return a;
    }
    if (op == ",") return b;
    if (op == "==") return Value::boolean(equals(a, b));
    if (op == "!=") return Value::boolean(!equals(a, b));
    auto bad = [&]() { fail("invalid operands to '" + op + "' (" + a.typeName() + " and " + b.typeName() + ")", line); };
    if (a.t == T::Str || b.t == T::Str) {
        if (op == "+") return Value::str(a.toString() + b.toString());
        if (a.t == T::Str && b.t == T::Str) {
            int c = a.s->compare(*b.s);
            if (op == "<") return Value::boolean(c < 0);
            if (op == ">") return Value::boolean(c > 0);
            if (op == "<=") return Value::boolean(c <= 0);
            if (op == ">=") return Value::boolean(c >= 0);
        }
        bad();
    }
    if (a.t == T::Vec || b.t == T::Vec) {
        char o = op[0];
        if (a.t == T::Vec && b.t == T::Vec && op.size() == 1) {
            switch (o) {
                case '+': return Value::vec(a.v.x + b.v.x, a.v.y + b.v.y, a.v.z + b.v.z);
                case '-': return Value::vec(a.v.x - b.v.x, a.v.y - b.v.y, a.v.z - b.v.z);
                case '*': return Value::vec(a.v.x * b.v.x, a.v.y * b.v.y, a.v.z * b.v.z);
                case '/': return Value::vec(a.v.x / b.v.x, a.v.y / b.v.y, a.v.z / b.v.z);
            }
        }
        if (a.t == T::Vec && b.isNum() && op.size() == 1) {
            double s = b.num();
            if (o == '*') return Value::vec(a.v.x * s, a.v.y * s, a.v.z * s);
            if (o == '/') { if (s == 0) fail("Vec3 division by zero", line); return Value::vec(a.v.x / s, a.v.y / s, a.v.z / s); }
        }
        if (a.isNum() && b.t == T::Vec && op == "*") { double s = a.num(); return Value::vec(b.v.x * s, b.v.y * s, b.v.z * s); }
        bad();
    }
    if (a.t == T::List && b.t == T::List && op == "+") {
        Value r = Value::newList();
        *r.list = *a.list;
        r.list->insert(r.list->end(), b.list->begin(), b.list->end());
        return r;
    }
    if (!a.isNum() || !b.isNum()) bad();
    bool ints = a.t != T::Float && b.t != T::Float;
    const char o0 = op[0], o1 = op.size() > 1 ? op[1] : 0;
    if (ints) {
        int64_t x = a.toInt(), y = b.toInt();
        switch (o0) {
            case '+': return Value::integer(x + y);
            case '-': return Value::integer(x - y);
            case '*': return Value::integer(x * y);
            case '/': if (y == 0) fail("integer division by zero", line); return Value::integer(x / y);
            case '%': if (y == 0) fail("integer modulo by zero", line); return Value::integer(x % y);
            case '&': return Value::integer(x & y);
            case '|': return Value::integer(x | y);
            case '^': return Value::integer(x ^ y);
            case '<':
                if (o1 == '<') return Value::integer(x << (y & 63));
                return Value::boolean(o1 == '=' ? x <= y : x < y);
            case '>':
                if (o1 == '>') return Value::integer(x >> (y & 63));
                return Value::boolean(o1 == '=' ? x >= y : x > y);
        }
        bad();
    }
    double x = a.num(), y = b.num();
    switch (o0) {
        case '+': return Value::number(x + y);
        case '-': return Value::number(x - y);
        case '*': return Value::number(x * y);
        case '/': return Value::number(x / y);
        case '%': return Value::number(fmod(x, y));
        case '<': if (o1 == '<') bad(); return Value::boolean(o1 == '=' ? x <= y : x < y);
        case '>': if (o1 == '>') bad(); return Value::boolean(o1 == '=' ? x >= y : x > y);
    }
    bad();
    return Value();
}

Value Interpreter::evalIdentFallback(Node* n) {
    const std::string& name = n->op;
    Instance* s = self();
    if (name == "gameObject" || name == "self" || name == "transform" || name == "owner" || name == "actor") {
        if (s && s->selfId) return Value::obj(s->selfId);
        return Value();
    }
    if (name == "cout" || name == "cerr" || name == "clog") { Value r; r.t = T::Stream; return r; }
    if (name == "endl") { Value r; r.t = T::Endl; return r; }
    if (name == "PI" || name == "M_PI" || name == "Math::PI" || name == "Mathf::PI" || name == "numbers::pi") return Value::number(M_PI);
    if (name == "TAU" || name == "Math::TAU") return Value::number(2 * M_PI);
    if (name == "Deg2Rad" || name == "Mathf::Deg2Rad" || name == "Math::Deg2Rad") return Value::number(M_PI / 180.0);
    if (name == "Rad2Deg" || name == "Mathf::Rad2Deg" || name == "Math::Rad2Deg") return Value::number(180.0 / M_PI);
    if (name == "INFINITY" || name == "FLT_MAX" || name == "Mathf::Infinity") return Value::number(name == "FLT_MAX" ? 3.402823466e38 : INFINITY);
    if (name == "EPSILON" || name == "FLT_EPSILON") return Value::number(1.1920929e-7);
    if (name == "INT_MAX") return Value::integer(2147483647);
    if (name == "INT_MIN") return Value::integer(-2147483647 - 1);
    if (name == "npos" || name == "string::npos") return Value::integer(-1);
    size_t p = name.rfind("::");
    if (p != std::string::npos) {
        std::string owner = name.substr(0, p), member = name.substr(p + 2);
        if (owner == "Vec3" || owner == "Vector3" || owner == "FVector") {
            std::string m = lower(member);
            if (m.find("zero") != std::string::npos) return Value::vec(0, 0, 0);
            if (m.find("one") != std::string::npos) return Value::vec(1, 1, 1);
            if (m.find("up") != std::string::npos) return Value::vec(0, 1, 0);
            if (m.find("down") != std::string::npos) return Value::vec(0, -1, 0);
            if (m.find("left") != std::string::npos) return Value::vec(-1, 0, 0);
            if (m.find("right") != std::string::npos) return Value::vec(1, 0, 0);
            if (m.find("forward") != std::string::npos) return Value::vec(0, 0, -1);
            if (m.find("back") != std::string::npos) return Value::vec(0, 0, 1);
        }
        auto cit = prog->classes.find(owner);
        if (cit != prog->classes.end()) {
            ClassDef* c = cit->second;
            auto fit = c->fieldIndex.find(prog->intern(member));
            if (fit != c->fieldIndex.end() && c->fields[fit->second].isStatic) { initStatics(c); return c->statics[fit->second]; }
            fail("'" + member + "' is not a static member of " + owner, n->line);
        }
        std::vector<Value> none;
        return hostCall(0, "get:" + name, none, n->line);
    }
    fail("'" + name + "' was not declared in this scope", n->line);
}

Value Interpreter::eval(Node* n) {
    switch (n->k) {
        case N::Num: return n->isInt ? Value::integer((int64_t) n->num) : Value::number(n->num);
        case N::Str: return Value::str(n->op);
        case N::Bool: return Value::boolean(n->num != 0);
        case N::Nil: return Value();
        case N::Ident: {
            char kind;
            Value* p = lookup(n, kind);
            if (p) return *p;
            return evalIdentFallback(n);
        }
        case N::This: {
            if (frames.empty() || !frames.back().self) fail("'this' used outside of a class", n->line);
            Value r;
            r.t = T::Inst;
            r.inst = frames.back().self;
            return r;
        }
        case N::Unary: {
            Value v = eval(n->c[0]);
            char o = n->op[0];
            switch (o) {
                case '-':
                    if (v.t == T::Int) return Value::integer(-v.i);
                    if (v.t == T::Vec) return Value::vec(-v.v.x, -v.v.y, -v.v.z);
                    if (v.isNum()) return Value::number(-v.num());
                    fail(std::string("cannot negate ") + v.typeName(), n->line);
                case '!': return Value::boolean(!v.truthy());
                case '~': return Value::integer(~v.toInt());
                default: return v;  // unary +, *deref, &address-of
            }
        }
        case N::Binary: {
            Value a = eval(n->c[0]);
            Value b = eval(n->c[1]);
            return binary(n->op, a, b, n->line);
        }
        case N::And: return Value::boolean(eval(n->c[0]).truthy() && eval(n->c[1]).truthy());
        case N::Or: return Value::boolean(eval(n->c[0]).truthy() || eval(n->c[1]).truthy());
        case N::Ternary: return eval(n->c[0]).truthy() ? eval(n->c[1]) : eval(n->c[2]);
        case N::Assign: {
            Value v = eval(n->c[1]);
            if (n->op != "=") {
                Value cur = eval(n->c[0]);
                std::string op = n->op.substr(0, n->op.size() - 1);
                if (cur.t == T::List && op == "+" && v.t != T::List) {  // vec += x (push) convenience
                    cur.list->push_back(v);
                    return cur;
                }
                v = binary(op, cur, v, n->line);
            }
            assign(n->c[0], v);
            return v;
        }
        case N::PreInc:
        case N::PostInc: {
            Value v = eval(n->c[0]);
            if (!v.isNum()) fail(std::string("cannot increment ") + v.typeName(), n->line);
            int d = n->op == "++" ? 1 : -1;
            Value nv = v.t == T::Float ? Value::number(v.f + d) : Value::integer(v.toInt() + d);
            assign(n->c[0], nv);
            return n->k == N::PreInc ? nv : v;
        }
        case N::Call: return evalCall(n);
        case N::Member: return memberGet(eval(n->c[0]), n->op, n->line);
        case N::Index: {
            Value base = eval(n->c[0]);
            Value idx = eval(n->c[1]);
            if (base.t == T::List) {
                int64_t k = idx.toInt();
                if (k < 0 || k >= (int64_t) base.list->size())
                    fail("index " + std::to_string(k) + " out of range (size " + std::to_string(base.list->size()) + ")", n->line);
                return (*base.list)[(size_t) k];
            }
            if (base.t == T::Str) {
                int64_t k = idx.toInt();
                if (k < 0 || k >= (int64_t) base.s->size()) fail("string index out of range", n->line);
                return Value::str(std::string(1, (*base.s)[(size_t) k]));
            }
            if (base.t == T::Dict) {
                std::string key = idx.toString();
                const Value* v = base.dict->get(key);
                if (!v) { base.dict->set(key, Value::integer(0)); return Value::integer(0); }  // std::map operator[] inserts
                return *v;
            }
            if (base.t == T::Vec) {
                int64_t k = idx.toInt();
                return Value::number(k == 0 ? base.v.x : k == 1 ? base.v.y : base.v.z);
            }
            fail(std::string("cannot index ") + base.typeName(), n->line);
        }
        case N::Cast: {
            Value v = eval(n->c[0]);
            const std::string& t = n->op;
            if (t == "int") return Value::integer(v.toInt());
            if (t == "float") return Value::number(v.num());
            if (t == "bool") return Value::boolean(v.truthy());
            if (t == "string") return Value::str(v.toString());
            return v;
        }
        case N::New:
        case N::Construct: {
            std::vector<Value> args;
            for (Node* a : n->c) args.push_back(eval(a));
            if (n->op == "List" && n->isInt) {
                Value r = Value::newList();
                *r.list = args;
                return r;
            }
            if (n->op == "Vec3" || n->op == "string" || n->op == "int" || n->op == "float" || n->op == "bool" || n->op == "GameObject" ||
                n->op == "List" || n->op == "Dict" || prog->classes.count(n->op))
                return construct(n->op, args, n->line);
            fail("unknown type '" + n->op + "'", n->line);
        }
        case N::InitList: {
            Value r = Value::newList();
            for (Node* a : n->c) r.list->push_back(eval(a));
            return r;
        }
        case N::DeclGroup: {
            // declaration inside a condition: if (auto e = Find("x")) — value of the declared variable
            exec(n);
            return stack.back().v;
        }
        default:
            fail("unsupported expression", n->line);
    }
}

void Interpreter::assign(Node* target, const Value& v) {
    switch (target->k) {
        case N::Ident: {
            char kind;
            Value* p = lookup(target, kind);
            if (p) { *p = coerce(kind, v); return; }
            const std::string& name = target->op;
            size_t sep = name.rfind("::");
            if (sep != std::string::npos) {
                auto cit = prog->classes.find(name.substr(0, sep));
                if (cit != prog->classes.end()) {
                    ClassDef* c = cit->second;
                    auto fit = c->fieldIndex.find(prog->intern(name.substr(sep + 2)));
                    if (fit != c->fieldIndex.end() && c->fields[fit->second].isStatic) {
                        initStatics(c);
                        c->statics[fit->second] = coerce(c->fields[fit->second].kind, v);
                        return;
                    }
                }
                std::vector<Value> a = {v};
                hostCall(0, "set:" + name, a, target->line);
                return;
            }
            fail("'" + name + "' was not declared in this scope", target->line);
        }
        case N::Member: {
            Value base = eval(target->c[0]);
            const std::string& m = target->op;
            if (base.t == T::Obj) {
                if (base.i == 0) fail("assigning '" + m + "' on a null GameObject", target->line);
                std::vector<Value> a = {v};
                hostCall(base.i, "set:" + m, a, target->line);
                return;
            }
            if (base.t == T::Inst) {
                auto it = base.inst->cls->fieldIndex.find(prog->intern(m));
                if (it == base.inst->cls->fieldIndex.end()) fail("'" + base.inst->cls->name + "' has no member '" + m + "'", target->line);
                FieldDef& fd = base.inst->cls->fields[it->second];
                (fd.isStatic ? base.inst->cls->statics[it->second] : base.inst->fields[it->second]) = coerce(fd.kind, v);
                return;
            }
            if (base.t == T::Vec) {
                Value nv = base;
                double d = v.num();
                if (m == "x" || m == "X" || m == "r") nv.v.x = d;
                else if (m == "y" || m == "Y" || m == "g") nv.v.y = d;
                else if (m == "z" || m == "Z" || m == "b") nv.v.z = d;
                else fail("Vec3 has no member '" + m + "'", target->line);
                assign(target->c[0], nv);
                return;
            }
            if (base.t == T::Dict) { base.dict->set(m, v); return; }
            if (base.t == T::Nil) fail("assigning '" + m + "' on nullptr", target->line);
            fail(std::string("cannot assign member of ") + base.typeName(), target->line);
        }
        case N::Index: {
            Value base = eval(target->c[0]);
            Value idx = eval(target->c[1]);
            if (base.t == T::List) {
                int64_t k = idx.toInt();
                if (k < 0 || k >= (int64_t) base.list->size())
                    fail("index " + std::to_string(k) + " out of range (size " + std::to_string(base.list->size()) + ")", target->line);
                (*base.list)[(size_t) k] = v;
                return;
            }
            if (base.t == T::Dict) { base.dict->set(idx.toString(), v); return; }
            if (base.t == T::Str) {
                int64_t k = idx.toInt();
                if (k < 0 || k >= (int64_t) base.s->size()) fail("string index out of range", target->line);
                std::string s = *base.s;
                std::string r = v.toString();
                s[(size_t) k] = r.empty() ? ' ' : r[0];
                assign(target->c[0], Value::str(s));
                return;
            }
            if (base.t == T::Vec) {
                Value nv = base;
                int64_t k = idx.toInt();
                (k == 0 ? nv.v.x : k == 1 ? nv.v.y : nv.v.z) = v.num();
                assign(target->c[0], nv);
                return;
            }
            fail(std::string("cannot index ") + base.typeName(), target->line);
        }
        case N::Unary:
            if (target->op == "*") { assign(target->c[0], v); return; }
            break;
        default: break;
    }
    fail("expression is not assignable", target->line);
}

Value Interpreter::memberGet(const Value& base, const std::string& m, int line) {
    switch (base.t) {
        case T::Obj: {
            if (base.i == 0) fail("reading '" + m + "' of a null GameObject", line);
            std::vector<Value> none;
            return hostCall(base.i, "get:" + m, none, line);
        }
        case T::Vec: {
            if (m == "x" || m == "X" || m == "r") return Value::number(base.v.x);
            if (m == "y" || m == "Y" || m == "g") return Value::number(base.v.y);
            if (m == "z" || m == "Z" || m == "b") return Value::number(base.v.z);
            double len = sqrt(base.v.x * base.v.x + base.v.y * base.v.y + base.v.z * base.v.z);
            if (m == "magnitude" || m == "length") return Value::number(len);
            if (m == "sqrMagnitude") return Value::number(len * len);
            if (m == "normalized") return len > 1e-12 ? Value::vec(base.v.x / len, base.v.y / len, base.v.z / len) : Value::vec(0, 0, 0);
            fail("Vec3 has no member '" + m + "'", line);
        }
        case T::Inst: {
            auto it = base.inst->cls->fieldIndex.find(prog->intern(m));
            if (it == base.inst->cls->fieldIndex.end()) fail("'" + base.inst->cls->name + "' has no member '" + m + "'", line);
            return base.inst->cls->fields[it->second].isStatic ? base.inst->cls->statics[it->second] : base.inst->fields[it->second];
        }
        case T::Dict: {
            const Value* v = base.dict->get(m);
            return v ? *v : Value();
        }
        case T::Nil: fail("reading '" + m + "' of nullptr", line);
        default: fail(std::string(base.typeName()) + " has no member '" + m + "'", line);
    }
}

Value Interpreter::methodCall(Value& base, Node* baseNode, const std::string& m, std::vector<Value>& args, int line) {
    auto argN = [&](size_t k) { return k < args.size() ? args[k].num() : 0.0; };
    switch (base.t) {
        case T::Obj:
            if (base.i == 0) fail("called '" + m + "()' on a null GameObject", line);
            return hostCall(base.i, m, args, line);
        case T::Nil:
            fail("called '" + m + "()' on nullptr", line);
        case T::Inst: {
            FuncDef* f = findMethod(base.inst->cls, m);
            if (!f) fail("'" + base.inst->cls->name + "' has no method '" + m + "'", line);
            return invoke(f, base.inst, args, line);
        }
        case T::Vec: {
            const Vec3& a = base.v;
            double len = sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
            std::string lm = lower(m);
            if (lm == "length" || lm == "magnitude" || lm == "size") return Value::number(len);
            if (lm == "sqrlength" || lm == "sqrmagnitude" || lm == "sizesquared" || lm == "length2") return Value::number(len * len);
            if (lm == "normalized" || lm == "getsafenormal" || lm == "getnormalized") return len > 1e-12 ? Value::vec(a.x / len, a.y / len, a.z / len) : Value::vec(0, 0, 0);
            if (lm == "normalize") {
                Value nv = len > 1e-12 ? Value::vec(a.x / len, a.y / len, a.z / len) : Value::vec(0, 0, 0);
                if (baseNode) assign(baseNode, nv);
                return Value::boolean(len > 1e-12);
            }
            if (lm == "set") {
                Value nv = Value::vec(argN(0), argN(1), argN(2));
                if (baseNode) assign(baseNode, nv);
                return Value();
            }
            if (args.empty() || args[0].t != T::Vec) {
                if (lm == "scale" || lm == "scaled") return Value::vec(a.x * argN(0), a.y * argN(0), a.z * argN(0));
                if (lm == "tostring") return Value::str(base.toString());
                fail("Vec3::" + m + " is not a known method", line);
            }
            const Vec3& b = args[0].v;
            if (lm == "dot") return Value::number(a.x * b.x + a.y * b.y + a.z * b.z);
            if (lm == "cross") return Value::vec(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
            if (lm == "distance" || lm == "distanceto" || lm == "dist") {
                double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
                return Value::number(sqrt(dx * dx + dy * dy + dz * dz));
            }
            if (lm == "lerp") { double t = argN(1); return Value::vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t); }
            fail("Vec3::" + m + " is not a known method", line);
        }
        case T::Str: {
            const std::string& s = *base.s;
            if (m == "length" || m == "size" || m == "Len") return Value::integer((int64_t) s.size());
            if (m == "empty" || m == "IsEmpty") return Value::boolean(s.empty());
            if (m == "c_str" || m == "str" || m == "data") return base;
            if (m == "substr" || m == "Mid") {
                int64_t p = args.empty() ? 0 : args[0].toInt();
                if (p < 0 || p > (int64_t) s.size()) fail("substr position out of range", line);
                int64_t l = args.size() > 1 ? args[1].toInt() : (int64_t) s.size();
                return Value::str(s.substr((size_t) p, l < 0 ? std::string::npos : (size_t) l));
            }
            if (m == "find" || m == "Find" || m == "rfind") {
                std::string needle = args.empty() ? "" : args[0].toString();
                size_t r = m == "rfind" ? s.rfind(needle) : s.find(needle, args.size() > 1 ? (size_t) args[1].toInt() : 0);
                return Value::integer(r == std::string::npos ? -1 : (int64_t) r);
            }
            if (m == "contains" || m == "Contains") return Value::boolean(s.find(args.empty() ? "" : args[0].toString()) != std::string::npos);
            if (m == "starts_with" || m == "StartsWith") { std::string p = args.empty() ? "" : args[0].toString(); return Value::boolean(s.rfind(p, 0) == 0); }
            if (m == "ends_with" || m == "EndsWith") {
                std::string p = args.empty() ? "" : args[0].toString();
                return Value::boolean(s.size() >= p.size() && s.compare(s.size() - p.size(), p.size(), p) == 0);
            }
            if (m == "at") { int64_t k = args.empty() ? 0 : args[0].toInt(); if (k < 0 || k >= (int64_t) s.size()) fail("string index out of range", line); return Value::str(std::string(1, s[(size_t) k])); }
            if (m == "compare") { int c = s.compare(args.empty() ? "" : args[0].toString()); return Value::integer(c < 0 ? -1 : c > 0 ? 1 : 0); }
            if (m == "ToUpper" || m == "toupper" || m == "upper") { std::string r = s; for (char& c : r) c = (char) toupper((unsigned char) c); return Value::str(r); }
            if (m == "ToLower" || m == "tolower" || m == "lower") return Value::str(lower(s));
            if (m == "append" || m == "push_back" || m == "Append" || m == "clear" || m == "pop_back" || m == "insert" || m == "erase") {
                std::string r = s;
                if (m == "append" || m == "push_back" || m == "Append") r += args.empty() ? "" : args[0].toString();
                else if (m == "clear") r.clear();
                else if (m == "pop_back") { if (!r.empty()) r.pop_back(); }
                else if (m == "insert") r.insert(std::min((size_t) (args.empty() ? 0 : args[0].toInt()), r.size()), args.size() > 1 ? args[1].toString() : "");
                else if (m == "erase") { size_t p = std::min((size_t) (args.empty() ? 0 : args[0].toInt()), r.size()); r.erase(p, args.size() > 1 ? (size_t) args[1].toInt() : std::string::npos); }
                if (baseNode) assign(baseNode, Value::str(r));
                return Value::str(r);
            }
            fail("std::string has no method '" + m + "'", line);
        }
        case T::List: {
            List& l = *base.list;
            if (m == "push_back" || m == "emplace_back" || m == "Add" || m == "push" || m == "add") { l.push_back(args.empty() ? Value() : args[0]); return Value(); }
            if (m == "size" || m == "Num" || m == "length" || m == "Count" || m == "count") {
                if ((m == "count") && !args.empty()) { int64_t c = 0; for (auto& e : l) if (equals(e, args[0])) c++; return Value::integer(c); }
                return Value::integer((int64_t) l.size());
            }
            if (m == "empty" || m == "IsEmpty") return Value::boolean(l.empty());
            if (m == "clear" || m == "Empty" || m == "Reset") { l.clear(); return Value(); }
            if (m == "pop_back" || m == "Pop") { if (l.empty()) fail("pop_back on an empty vector", line); Value r = l.back(); l.pop_back(); return r; }
            if (m == "back" || m == "Last") { if (l.empty()) fail("back() on an empty vector", line); return l.back(); }
            if (m == "front") { if (l.empty()) fail("front() on an empty vector", line); return l.front(); }
            if (m == "at" || m == "get") {
                int64_t k = args.empty() ? 0 : args[0].toInt();
                if (k < 0 || k >= (int64_t) l.size()) fail("index " + std::to_string(k) + " out of range (size " + std::to_string(l.size()) + ")", line);
                return l[(size_t) k];
            }
            if (m == "erase" || m == "RemoveAt" || m == "removeAt") {
                int64_t k = args.empty() ? 0 : args[0].toInt();
                if (k < 0 || k >= (int64_t) l.size()) fail("erase index out of range", line);
                l.erase(l.begin() + k);
                return Value();
            }
            if (m == "insert" || m == "Insert") {
                int64_t k = args.empty() ? 0 : args[0].toInt();
                if (k < 0 || k > (int64_t) l.size()) fail("insert index out of range", line);
                l.insert(l.begin() + k, args.size() > 1 ? args[1] : Value());
                return Value();
            }
            if (m == "contains" || m == "Contains") { for (auto& e : l) if (equals(e, args.empty() ? Value() : args[0])) return Value::boolean(true); return Value::boolean(false); }
            if (m == "indexOf" || m == "Find" || m == "find") { for (size_t k = 0; k < l.size(); k++) if (equals(l[k], args.empty() ? Value() : args[0])) return Value::integer((int64_t) k); return Value::integer(-1); }
            if (m == "remove" || m == "Remove") {
                int64_t removed = 0;
                for (size_t k = l.size(); k > 0; k--) if (equals(l[k - 1], args.empty() ? Value() : args[0])) { l.erase(l.begin() + (long) (k - 1)); removed++; }
                return Value::integer(removed);
            }
            if (m == "resize" || m == "SetNum") { int64_t n = args.empty() ? 0 : args[0].toInt(); if (n < 0 || n > 10000000) fail("bad size", line); l.resize((size_t) n, args.size() > 1 ? args[1] : Value::integer(0)); return Value(); }
            if (m == "reserve") return Value();
            if (m == "sort" || m == "Sort") {
                std::stable_sort(l.begin(), l.end(), [](const Value& x, const Value& y) {
                    if (x.t == T::Str && y.t == T::Str) return *x.s < *y.s;
                    return x.num() < y.num();
                });
                return Value();
            }
            if (m == "reverse") { std::reverse(l.begin(), l.end()); return Value(); }
            fail("std::vector has no method '" + m + "'", line);
        }
        case T::Dict: {
            Dict& d = *base.dict;
            std::string key = args.empty() ? "" : args[0].toString();
            if (m == "count" || m == "contains" || m == "Contains" || m == "find") return Value::boolean(d.get(key) != nullptr);
            if (m == "size" || m == "Num") return Value::integer((int64_t) d.keys.size());
            if (m == "empty") return Value::boolean(d.keys.empty());
            if (m == "at" || m == "Find" || m == "get") { const Value* v = d.get(key); if (!v && m == "at") fail("map::at: key '" + key + "' not found", line); return v ? *v : Value(); }
            if (m == "insert" || m == "Add" || m == "set" || m == "emplace") { d.set(key, args.size() > 1 ? args[1] : Value()); return Value(); }
            if (m == "erase" || m == "Remove") {
                for (size_t k = 0; k < d.keys.size(); k++) if (d.keys[k] == key) { d.keys.erase(d.keys.begin() + (long) k); d.values.erase(d.values.begin() + (long) k); return Value::integer(1); }
                return Value::integer(0);
            }
            if (m == "clear") { d.keys.clear(); d.values.clear(); return Value(); }
            fail("std::map has no method '" + m + "'", line);
        }
        default:
            fail(std::string(base.typeName()) + " has no method '" + m + "'", line);
    }
}

Value Interpreter::evalCall(Node* n) {
    Node* callee = n->c[0];
    std::vector<Value> args;
    args.reserve(n->c.size() - 1);
    bool ueLog = callee->k == N::Ident && callee->op == "UE_LOG";
    for (size_t k = 1; k < n->c.size(); k++) {
        // UE_LOG(LogTemp, Warning, …): category and verbosity are bare names
        if (ueLog && k <= 2 && n->c[k]->k == N::Ident) args.push_back(Value::str(n->c[k]->op));
        else args.push_back(eval(n->c[k]));
    }
    if (callee->k == N::Member) {
        Value base = eval(callee->c[0]);
        return methodCall(base, callee->c[0], callee->op, args, n->line);
    }
    if (callee->k != N::Ident) fail("expression is not callable", n->line);
    const std::string& name = callee->op;
    if (!frames.empty() && frames.back().self) {
        if (FuncDef* f = findMethod(frames.back().self->cls, name)) return invoke(f, frames.back().self, args, n->line);
    }
    auto fit = prog->functions.find(name);
    if (fit != prog->functions.end()) return invoke(fit->second, nullptr, args, n->line);
    if (prog->classes.count(name)) return construct(name, args, n->line);
    size_t sep = name.rfind("::");
    if (sep != std::string::npos) {
        std::string owner = name.substr(0, sep), m = name.substr(sep + 2);
        auto cit = prog->classes.find(owner);
        if (owner == "Super" || owner == "Base" || cit != prog->classes.end()) {
            ClassDef* c = cit != prog->classes.end() ? cit->second : (self() ? self()->cls->baseClass : nullptr);
            FuncDef* f = c ? findMethod(c, m) : nullptr;
            if (f) {
                std::shared_ptr<Instance> s = f->isStatic || frames.empty() ? nullptr : frames.back().self;
                return invoke(f, s, args, n->line);
            }
            if (cit != prog->classes.end()) fail("'" + owner + "' has no method '" + m + "'", n->line);
            return Value();  // Super::BeginPlay() on an engine base class: nothing to do
        }
        static const char* BASES[] = {"Behaviour", "MonoBehaviour", "AActor", "Actor", "UActorComponent", "Component", "APawn", "ACharacter", nullptr};
        for (int k = 0; BASES[k]; k++) if (owner == BASES[k]) return Value();
    }
    bool ok = false;
    Value r = builtin(name, args, n->line, ok);
    if (ok) return r;
    if (name == "Vec3" || name == "Vector3" || name == "FVector" || name == "string" || name == "int" || name == "float" ||
        name == "double" || name == "bool" || name == "FString")
        return construct(name, args, n->line);
    return hostCall(0, name, args, n->line);
}

// ================================================================ built-in library
Value Interpreter::builtin(const std::string& nameIn, std::vector<Value>& a, int line, bool& ok) {
    ok = true;
    auto need = [&](size_t n) {
        if (a.size() < n) fail(nameIn + "() needs " + std::to_string(n) + " argument" + (n == 1 ? "" : "s"), line);
    };
    auto num = [&](size_t k) { return k < a.size() ? a[k].num() : 0.0; };
    // logging (checked case-sensitively first: Log("x") prints, log(2.0) is the natural logarithm)
    if (nameIn == "Log" || nameIn == "Print" || nameIn == "print" || nameIn == "println" || nameIn == "puts" || nameIn == "Debug::Log" ||
        nameIn == "Warn" || nameIn == "Debug::LogWarning" || nameIn == "Error" || nameIn == "Debug::LogError" ||
        (nameIn == "log" && !a.empty() && a[0].t == T::Str)) {
        std::string msg;
        for (size_t k = 0; k < a.size(); k++) { if (k) msg += " "; msg += a[k].toString(); }
        int level = (nameIn == "Warn" || nameIn == "Debug::LogWarning") ? 1 : (nameIn == "Error" || nameIn == "Debug::LogError") ? 2 : 0;
        flushCout(true);
        if (host) host->log(level, msg);
        return Value();
    }
    if (nameIn == "printf" || nameIn == "Printf" || nameIn == "FString::Printf" || nameIn == "sprintf" || nameIn == "format" || nameIn == "Format") {
        need(1);
        std::string s = formatPrintf(a[0].toString(), a, 1);
        if (nameIn == "printf" || nameIn == "Printf") {
            coutBuf += s;
            flushCout(false);
            return Value::integer((int64_t) s.size());
        }
        return Value::str(s);
    }
    if (nameIn == "UE_LOG") {
        // UE_LOG(LogTemp, Warning, TEXT("Score %d"), Score)
        std::string level = a.size() > 1 ? a[1].toString() : "";
        std::string s = a.size() > 2 ? formatPrintf(a[2].toString(), a, 3) : "";
        if (host) host->log(level == "Error" || level == "Fatal" ? 2 : level == "Warning" ? 1 : 0, s);
        return Value();
    }
    std::string name = nameIn;
    for (const char* pre : {"Math::", "Mathf::", "FMath::", "glm::", "Random::"}) {
        size_t len = strlen(pre);
        if (name.compare(0, len, pre) == 0) {
            if (std::string(pre) == "Random::") name = "Random" + name.substr(len);
            else name = name.substr(len);
            break;
        }
    }
    std::string l = lower(name);
    // float variants: sinf, sqrtf, fabsf …
    static const char* FVARIANTS[] = {"sinf", "cosf", "tanf", "sqrtf", "fabsf", "powf", "floorf", "ceilf", "roundf", "fminf", "fmaxf",
                                      "atan2f", "expf", "logf", "fmodf", "asinf", "acosf", "atanf", "truncf", "hypotf", nullptr};
    for (int k = 0; FVARIANTS[k]; k++) if (l == FVARIANTS[k]) { l.pop_back(); break; }
    if (l == "sin") { need(1); return Value::number(sin(num(0))); }
    if (l == "cos") { need(1); return Value::number(cos(num(0))); }
    if (l == "tan") { need(1); return Value::number(tan(num(0))); }
    if (l == "asin") { need(1); return Value::number(asin(num(0))); }
    if (l == "acos") { need(1); return Value::number(acos(num(0))); }
    if (l == "atan") { need(1); return Value::number(atan(num(0))); }
    if (l == "atan2") { need(2); return Value::number(atan2(num(0), num(1))); }
    if (l == "sqrt") { need(1); return Value::number(sqrt(num(0))); }
    if (l == "pow") { need(2); return Value::number(pow(num(0), num(1))); }
    if (l == "exp") { need(1); return Value::number(exp(num(0))); }
    if (l == "log") { need(1); return Value::number(log(num(0))); }
    if (l == "log10") { need(1); return Value::number(log10(num(0))); }
    if (l == "log2") { need(1); return Value::number(log2(num(0))); }
    if (l == "hypot") { need(2); return Value::number(hypot(num(0), num(1))); }
    if (l == "abs" || l == "fabs") { need(1); return a[0].t == T::Int ? Value::integer(a[0].i < 0 ? -a[0].i : a[0].i) : Value::number(fabs(num(0))); }
    if (l == "floor" || l == "floortoint") { need(1); return l == "floortoint" ? Value::integer((int64_t) floor(num(0))) : Value::number(floor(num(0))); }
    if (l == "ceil" || l == "ceiltoint") { need(1); return l == "ceiltoint" ? Value::integer((int64_t) ceil(num(0))) : Value::number(ceil(num(0))); }
    if (l == "round" || l == "roundtoint") { need(1); return l == "roundtoint" ? Value::integer((int64_t) llround(num(0))) : Value::number(round(num(0))); }
    if (l == "trunc" || l == "trunctoint") { need(1); return l == "trunctoint" ? Value::integer((int64_t) num(0)) : Value::number(trunc(num(0))); }
    if (l == "fmod") { need(2); return Value::number(fmod(num(0), num(1))); }
    if (l == "min" || l == "max" || l == "fmin" || l == "fmax") {
        need(2);
        bool isMin = l == "min" || l == "fmin";
        Value best = a[0];
        for (size_t k = 1; k < a.size(); k++) if (isMin ? a[k].num() < best.num() : a[k].num() > best.num()) best = a[k];
        if (a[0].t == T::Int && a[1].t == T::Int) return Value::integer(best.toInt());
        return Value::number(best.num());
    }
    if (l == "clamp") {
        need(3);
        if (a[0].t == T::Int && a[1].t == T::Int && a[2].t == T::Int) return Value::integer(std::max(a[1].i, std::min(a[2].i, a[0].i)));
        return Value::number(std::max(num(1), std::min(num(2), num(0))));
    }
    if (l == "clamp01" || l == "saturate") { need(1); return Value::number(std::max(0.0, std::min(1.0, num(0)))); }
    if (l == "lerp" || l == "mix") {
        need(3);
        if (a[0].t == T::Vec && a[1].t == T::Vec) { double t = num(2); return Value::vec(a[0].v.x + (a[1].v.x - a[0].v.x) * t, a[0].v.y + (a[1].v.y - a[0].v.y) * t, a[0].v.z + (a[1].v.z - a[0].v.z) * t); }
        return Value::number(num(0) + (num(1) - num(0)) * num(2));
    }
    if (l == "inverselerp") { need(3); double d = num(1) - num(0); return Value::number(d == 0 ? 0 : (num(2) - num(0)) / d); }
    if (l == "sign") { need(1); double v = num(0); return Value::number(v > 0 ? 1 : v < 0 ? -1 : 0); }
    if (l == "radians" || l == "deg2rad" || l == "degreestoradians") { need(1); return Value::number(num(0) * M_PI / 180.0); }
    if (l == "degrees" || l == "rad2deg" || l == "radianstodegrees") { need(1); return Value::number(num(0) * 180.0 / M_PI); }
    if (l == "movetowards" || l == "approach" || l == "fintericonstantto") {
        need(3);
        double v = num(0), t = num(1), s = fabs(num(2));
        return Value::number(fabs(t - v) <= s ? t : v + (t > v ? s : -s));
    }
    if (l == "smoothdamp" || l == "finterpto") { need(4); double t = 1 - exp(-num(2) * num(3)); return Value::number(num(0) + (num(1) - num(0)) * t); }
    if (l == "smoothstep") { need(3); double t = std::max(0.0, std::min(1.0, (num(2) - num(0)) / (num(1) - num(0)))); return Value::number(t * t * (3 - 2 * t)); }
    if (l == "pingpong") { need(2); double len = num(1), t = fmod(num(0), len * 2); if (t < 0) t += len * 2; return Value::number(len - fabs(t - len)); }
    if (l == "repeat" || l == "wrap") { need(2); double len = num(1), t = fmod(num(0), len); if (t < 0) t += len; return Value::number(t); }
    if (l == "isnan") { need(1); return Value::boolean(std::isnan(num(0))); }
    if (l == "distance" || l == "dist") {
        if (a.size() == 2 && a[0].t == T::Vec && a[1].t == T::Vec) {
            double dx = a[0].v.x - a[1].v.x, dy = a[0].v.y - a[1].v.y, dz = a[0].v.z - a[1].v.z;
            return Value::number(sqrt(dx * dx + dy * dy + dz * dz));
        }
        if (a.size() == 4) return Value::number(hypot(num(2) - num(0), num(3) - num(1)));
        if (a.size() == 6) { double dx = num(3) - num(0), dy = num(4) - num(1), dz = num(5) - num(2); return Value::number(sqrt(dx * dx + dy * dy + dz * dz)); }
    }
    if (l == "vec3::distance" || l == "vector3::distance" || l == "fvector::dist" || l == "fvector::distance") {
        need(2);
        double dx = a[0].v.x - a[1].v.x, dy = a[0].v.y - a[1].v.y, dz = a[0].v.z - a[1].v.z;
        return Value::number(sqrt(dx * dx + dy * dy + dz * dz));
    }
    if (l == "vec3::dot" || l == "vector3::dot" || l == "fvector::dotproduct" || l == "dot") { need(2); return Value::number(a[0].v.x * a[1].v.x + a[0].v.y * a[1].v.y + a[0].v.z * a[1].v.z); }
    if (l == "vec3::cross" || l == "vector3::cross" || l == "fvector::crossproduct" || l == "cross") {
        need(2);
        const Vec3 &p = a[0].v, &q = a[1].v;
        return Value::vec(p.y * q.z - p.z * q.y, p.z * q.x - p.x * q.z, p.x * q.y - p.y * q.x);
    }
    if (l == "vec3::lerp" || l == "vector3::lerp") { need(3); double t = num(2); return Value::vec(a[0].v.x + (a[1].v.x - a[0].v.x) * t, a[0].v.y + (a[1].v.y - a[0].v.y) * t, a[0].v.z + (a[1].v.z - a[0].v.z) * t); }
    if (l == "vec3::normalize" || l == "vector3::normalize" || l == "normalize") {
        need(1);
        const Vec3& p = a[0].v;
        double len = sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
        return len > 1e-12 ? Value::vec(p.x / len, p.y / len, p.z / len) : Value::vec(0, 0, 0);
    }
    if (l == "length" && a.size() == 1 && a[0].t == T::Vec) { const Vec3& p = a[0].v; return Value::number(sqrt(p.x * p.x + p.y * p.y + p.z * p.z)); }
    // random numbers
    if (l == "random" || l == "randomrange" || l == "randrange" || l == "frandrange" || l == "randomfloat" || l == "frand") {
        if (a.empty()) return Value::number(rnd01());
        if (a.size() == 1) return Value::number(rnd01() * num(0));
        return Value::number(num(0) + rnd01() * (num(1) - num(0)));
    }
    if (l == "randomint" || l == "randint" || l == "randhelper" || l == "randomintrange") {
        need(2);
        int64_t lo = a[0].toInt(), hi = a[1].toInt();
        if (hi < lo) std::swap(lo, hi);
        return Value::integer(lo + (int64_t) (rnd01() * (double) (hi - lo + 1)));
    }
    if (l == "randbool" || l == "randombool" || l == "chance") return Value::boolean(rnd01() < (a.empty() ? 0.5 : num(0)));
    if (l == "rand") return Value::integer((int64_t) (rnd01() * 32768.0));
    if (l == "srand" || l == "randomseed" || l == "seed") { rngState = (uint64_t) (a.empty() ? 1 : a[0].toInt()) * 2654435761ull + 1; return Value(); }
    // strings
    if (l == "to_string" || l == "tostring" || l == "fstring::fromint" || l == "fstring::sanitizefloat") {
        need(1);
        if (a[0].t == T::Float) { char buf[64]; snprintf(buf, sizeof buf, "%f", a[0].f); return Value::str(buf); }
        return Value::str(a[0].toString());
    }
    if (l == "stoi" || l == "atoi" || l == "stol" || l == "stoll") { need(1); return Value::integer((int64_t) strtoll(a[0].toString().c_str(), nullptr, 10)); }
    if (l == "stof" || l == "stod" || l == "atof") { need(1); return Value::number(strtod(a[0].toString().c_str(), nullptr)); }
    if (l == "strlen") { need(1); return Value::integer((int64_t) a[0].toString().size()); }
    if (l == "swap") fail("swap() needs references — swap the values manually", line);
    if (l == "make_pair" || l == "make_tuple") { Value r = Value::newList(); *r.list = a; return r; }
    ok = false;
    return Value();
}

}  // namespace sengine
