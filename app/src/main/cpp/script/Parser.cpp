#include "SScript.h"

#include <functional>
#include <unordered_set>

namespace sengine {

namespace {

const std::unordered_set<std::string> QUALIFIERS = {
    "const", "static", "constexpr", "inline", "virtual", "unsigned", "signed", "volatile", "mutable", "extern",
    "explicit", "struct", "class", "typename", "UPROPERTY", "UFUNCTION"};

/** Maps a C++ / UE type name to the VM's type: int, float, bool, string, Vec3, List, Dict, GameObject, auto, void or a class. */
std::string normalizeType(const std::string& raw) {
    std::string t = raw;
    if (t.rfind("std::", 0) == 0) t = t.substr(5);
    static const std::unordered_set<std::string> ints = {
        "int", "long", "short", "char", "size_t", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t",
        "uint32_t", "uint64_t", "int32", "int64", "uint8", "uint32", "unsigned", "signed", "ptrdiff_t"};
    static const std::unordered_set<std::string> floats = {"float", "double", "real", "float32", "float64"};
    if (ints.count(t)) return "int";
    if (floats.count(t)) return "float";
    if (t == "bool") return "bool";
    if (t == "string" || t == "FString" || t == "FName" || t == "FText" || t == "string_view" || t == "String") return "string";
    if (t == "Vec3" || t == "Vector3" || t == "FVector" || t == "vec3" || t == "glm::vec3" || t == "Vec2" || t == "Vector2") return "Vec3";
    if (t == "vector" || t == "list" || t == "array" || t == "deque" || t == "TArray" || t == "List") return "List";
    if (t == "map" || t == "unordered_map" || t == "TMap" || t == "Dict") return "Dict";
    if (t == "GameObject" || t == "AActor" || t == "Actor" || t == "Object" || t == "Entity" || t == "Transform") return "GameObject";
    if (t == "auto" || t == "void") return t;
    return t;
}

bool isBuiltinType(const std::string& norm) {
    return norm == "int" || norm == "float" || norm == "bool" || norm == "string" || norm == "Vec3" || norm == "List" ||
           norm == "Dict" || norm == "GameObject" || norm == "auto" || norm == "void";
}

class Parser {
public:
    Parser(std::vector<Token>& t, Program* p) : toks(t), prog(p) {
        // pre-scan class / struct / enum names so declarations can be told apart from expressions
        for (size_t i = 0; i + 1 < toks.size(); i++)
            if (toks[i].k == Tok::Ident && (toks[i].s == "class" || toks[i].s == "struct") && toks[i + 1].k == Tok::Ident)
                classNames.insert(toks[i + 1].s);
    }

    void parseProgram() {
        int nsDepth = 0;
        while (!at(Tok::End)) {
            if (isOp(";")) { pos++; continue; }
            if (isOp("}") && nsDepth > 0) { pos++; nsDepth--; continue; }
            if (isWord("using") || isWord("typedef") || isWord("friend")) { skipTo(";"); continue; }
            if (isWord("namespace")) {
                pos++;
                while (!isOp("{") && !at(Tok::End)) pos++;
                expectOp("{");
                nsDepth++;
                continue;
            }
            if (isWord("template")) { pos++; skipAngles(); continue; }
            if (isWord("enum")) { parseEnum(); continue; }
            if ((isWord("class") || isWord("struct")) && peek(1).k == Tok::Ident && (peekOp(2, "{") || peekOp(2, ":") || peekWord(2, "final"))) {
                parseClass();
                continue;
            }
            if ((isWord("class") || isWord("struct")) && peekOp(2, ";")) { pos += 3; continue; }  // forward declaration
            parseTopLevelDecl();
        }
    }

private:
    std::vector<Token>& toks;
    Program* prog;
    size_t pos = 0;
    std::unordered_set<std::string> classNames;
    std::string currentClass;

    const Token& cur() const { return toks[pos]; }
    const Token& peek(int k) const { return toks[std::min(pos + k, toks.size() - 1)]; }
    bool at(Tok k) const { return cur().k == k; }
    bool isOp(const char* s) const { return cur().k == Tok::Op && cur().s == s; }
    bool peekOp(int k, const char* s) const { return peek(k).k == Tok::Op && peek(k).s == s; }
    bool isWord(const char* s) const { return cur().k == Tok::Ident && cur().s == s; }
    bool peekWord(int k, const char* s) const { return peek(k).k == Tok::Ident && peek(k).s == s; }
    int line() const { return cur().line; }

    [[noreturn]] void error(const std::string& m) {
        std::string near = cur().k == Tok::End ? "end of file" : "'" + cur().s + "'";
        if (cur().k == Tok::Int || cur().k == Tok::Float) near = "number";
        throw ScriptError(m + " near " + near, line());
    }
    void expectOp(const char* s) {
        if (!isOp(s)) error(std::string("expected '") + s + "'");
        pos++;
    }
    /** Accepts '>' — splitting a '>>' token when closing nested templates. */
    void expectCloseAngle() {
        if (isOp(">")) { pos++; return; }
        if (isOp(">>")) { toks[pos].s = ">"; return; }
        if (isOp(">=")) { toks[pos].s = "="; return; }
        error("expected '>'");
    }
    std::string expectIdent() {
        if (!at(Tok::Ident)) error("expected a name");
        return toks[pos++].s;
    }
    void skipTo(const char* s) {
        while (!at(Tok::End) && !isOp(s)) pos++;
        if (isOp(s)) pos++;
    }
    void skipAngles() {
        if (!isOp("<")) return;
        int d = 0;
        do {
            if (isOp("<")) d++;
            else if (isOp(">")) d--;
            else if (isOp(">>")) d -= 2;
            pos++;
        } while (d > 0 && !at(Tok::End));
    }
    /** Skips balanced parentheses / attribute macros like UPROPERTY(EditAnywhere). */
    void skipParens() {
        if (!isOp("(")) return;
        int d = 0;
        do {
            if (isOp("(")) d++;
            else if (isOp(")")) d--;
            pos++;
        } while (d > 0 && !at(Tok::End));
    }

    bool isTypeName(const std::string& name) const {
        std::string n = name;
        if (n.rfind("std::", 0) == 0) n = n.substr(5);
        if (classNames.count(n)) return true;
        return isBuiltinType(normalizeType(n)) || n == "double" || n == "char" || n == "long";
    }

    /** Tries to parse a type at the current position. Returns "" (and restores pos) if there is none. */
    std::string tryType() {
        size_t start = pos;
        bool sawQualifier = false;
        while (at(Tok::Ident) && QUALIFIERS.count(cur().s)) {
            if (cur().s == "UPROPERTY" || cur().s == "UFUNCTION") { pos++; skipParens(); }
            else pos++;
            sawQualifier = true;
        }
        if (!at(Tok::Ident)) { pos = start; return ""; }
        std::string name = cur().s;
        size_t save = pos;
        pos++;
        while (isOp("::") && peek(1).k == Tok::Ident) { name += "::" + peek(1).s; pos += 2; }
        // "unsigned" alone, "long long", "unsigned int"
        while (at(Tok::Ident) && (cur().s == "int" || cur().s == "long" || cur().s == "short" || cur().s == "char") &&
               (name == "long" || name == "short" || name == "unsigned" || name == "signed")) { name = cur().s; pos++; }
        if (!isTypeName(name)) {
            if (!sawQualifier) { pos = start; return ""; }
            // qualifier followed by an unknown name: treat as a type (e.g. "const Foo&")
            (void) save;
        }
        std::string norm = normalizeType(name);
        if (isOp("<")) {
            // template arguments: element types are not tracked
            pos++;
            int d = 1;
            while (d > 0 && !at(Tok::End)) {
                if (isOp("<")) d++;
                else if (isOp(">")) d--;
                else if (isOp(">>")) { if (d >= 2) d -= 2; else { toks[pos].s = ">"; d--; continue; } }
                pos++;
            }
        }
        while (isOp("*") || isOp("&") || isOp("&&") || isWord("const")) pos++;
        if (name == "char" && toks[pos - 1].s == "*") norm = "string";
        return norm;
    }

    /** True if a declaration (type followed by a name) starts here. */
    bool declAhead() {
        size_t save = pos;
        std::string t = tryType();
        bool ok = !t.empty() && at(Tok::Ident) && !QUALIFIERS.count(cur().s);
        pos = save;
        return ok;
    }

    void parseEnum() {
        pos++;  // enum
        if (isWord("class") || isWord("struct")) pos++;
        std::string enumName;
        if (at(Tok::Ident)) enumName = toks[pos++].s;
        if (isOp(":")) { pos++; tryType(); }
        if (isOp(";")) { pos++; return; }
        expectOp("{");
        int64_t next = 0;
        while (!isOp("}")) {
            std::string n = expectIdent();
            if (isOp("=")) {
                pos++;
                bool neg = false;
                if (isOp("-")) { neg = true; pos++; }
                if (!(at(Tok::Int) || at(Tok::Char))) error("enum values must be integer constants");
                next = (int64_t) toks[pos++].num * (neg ? -1 : 1);
            }
            for (const std::string& full : {n, enumName.empty() ? n : enumName + "::" + n}) {
                Node* d = prog->node(N::Decl, line());
                d->op = "int";
                d->id = prog->intern(full);
                Node* v = prog->node(N::Num, line());
                v->num = (double) next; v->isInt = true;
                d->c.push_back(v);
                prog->globalDecls.push_back(d);
            }
            next++;
            if (isOp(",")) pos++;
            else break;
        }
        expectOp("}");
        if (isOp(";")) pos++;
    }

    void parseClass() {
        pos++;  // class / struct
        auto owned = std::make_unique<ClassDef>();
        ClassDef* cls = owned.get();
        cls->name = expectIdent();
        if (prog->classes.count(cls->name)) error("class '" + cls->name + "' is defined twice");
        prog->classArena.push_back(std::move(owned));
        prog->classes[cls->name] = cls;
        prog->classOrder.push_back(cls->name);
        if (isWord("final")) pos++;
        if (isOp(":")) {
            pos++;
            bool first = true;
            do {
                if (!first) pos++;
                first = false;
                while (isWord("public") || isWord("private") || isWord("protected") || isWord("virtual")) pos++;
                std::string b = expectIdent();
                while (isOp("::") && peek(1).k == Tok::Ident) { b += "::" + peek(1).s; pos += 2; }
                skipAngles();
                if (cls->base.empty()) cls->base = b;
            } while (isOp(","));
        }
        expectOp("{");
        std::string saved = currentClass;
        currentClass = cls->name;
        while (!isOp("}")) {
            if (at(Tok::End)) error("missing '}' at end of class " + cls->name);
            if ((isWord("public") || isWord("private") || isWord("protected")) && peekOp(1, ":")) { pos += 2; continue; }
            if (isWord("GENERATED_BODY") || isWord("GENERATED_UCLASS_BODY")) { pos++; skipParens(); continue; }
            if (isOp(";")) { pos++; continue; }
            if (isWord("friend") || isWord("using") || isWord("typedef")) { skipTo(";"); continue; }
            if (isWord("enum")) { parseEnum(); continue; }
            if (isWord("template")) { pos++; skipAngles(); continue; }
            parseMember(cls);
        }
        expectOp("}");
        if (isOp(";")) pos++;
        currentClass = saved;
    }

    void parseMember(ClassDef* cls) {
        // constructor / destructor
        size_t save = pos;
        while (isWord("explicit") || isWord("virtual") || isWord("inline")) pos++;
        if (isOp("~") && peek(1).s == cls->name) {
            pos += 2;
            FuncDef* f = parseFunctionRest("__dtor", "void");
            cls->methods["__dtor"] = f;
            return;
        }
        if (isWord(cls->name.c_str()) && peekOp(1, "(")) {
            pos++;
            FuncDef* f = parseFunctionRest("__ctor", "void", true);
            cls->methods["__ctor"] = f;
            return;
        }
        pos = save;
        bool isStatic = false;
        for (size_t k = pos; k < toks.size() && toks[k].k == Tok::Ident && QUALIFIERS.count(toks[k].s); k++)
            if (toks[k].s == "static") isStatic = true;
        std::string type = tryType();
        if (type.empty()) error("expected a member declaration in class " + cls->name);
        std::string name = expectIdent();
        if (name == "operator") error("operator overloading is not supported in scripts");
        if (isOp("(")) {
            FuncDef* f = parseFunctionRest(name, type);
            f->isStatic = isStatic;
            if (f->body) cls->methods[name] = f;
            return;
        }
        while (true) {
            FieldDef fd;
            fd.name = name;
            fd.id = prog->intern(name);
            fd.type = type;
            fd.isStatic = isStatic;
            while (isOp("[")) { pos++; if (!isOp("]")) parseExpr(); expectOp("]"); fd.type = "List"; }
            if (isOp("=")) { pos++; fd.init = parseInitializer(); }
            else if (isOp("{")) fd.init = parseInitList();
            else if (isOp("(")) fd.init = parseCtorArgs(type);
            cls->fields.push_back(fd);
            if (isOp(",")) { pos++; name = expectIdent(); continue; }
            break;
        }
        expectOp(";");
    }

    Node* parseCtorArgs(const std::string& type) {
        Node* n = prog->node(N::Construct, line());
        n->op = type;
        expectOp("(");
        while (!isOp(")")) {
            n->c.push_back(parseAssign());
            if (isOp(",")) pos++;
            else break;
        }
        expectOp(")");
        return n;
    }

    Node* parseInitializer() {
        if (isOp("{")) return parseInitList();
        return parseAssign();
    }

    Node* parseInitList() {
        Node* n = prog->node(N::InitList, line());
        expectOp("{");
        while (!isOp("}")) {
            if (isOp(".") && peek(1).k == Tok::Ident && peekOp(2, "=")) pos += 3;  // designated initialisers: .x = 1
            n->c.push_back(isOp("{") ? parseInitList() : parseAssign());
            if (isOp(",")) pos++;
            else break;
        }
        expectOp("}");
        return n;
    }

    /** After the function name: (params) [const] [override] { body } | ; */
    FuncDef* parseFunctionRest(const std::string& name, const std::string& retType, bool ctor = false) {
        auto owned = std::make_unique<FuncDef>();
        FuncDef* f = owned.get();
        prog->funcArena.push_back(std::move(owned));
        f->name = name;
        f->retType = retType;
        f->line = line();
        expectOp("(");
        if (isWord("void") && peekOp(1, ")")) pos++;
        while (!isOp(")")) {
            std::string pt = tryType();
            if (pt.empty()) error("expected a parameter type");
            std::string pn = at(Tok::Ident) ? toks[pos++].s : "_p" + std::to_string(f->paramIds.size());
            while (isOp("[")) { pos++; if (!isOp("]")) parseExpr(); expectOp("]"); pt = "List"; }
            f->paramTypes.push_back(pt);
            f->paramIds.push_back(prog->intern(pn));
            Node* def = nullptr;
            if (isOp("=")) { pos++; def = parseAssign(); }
            f->paramDefaults.push_back(def);
            if (isOp(",")) pos++;
            else if (isOp("...")) { pos++; }
            else break;
        }
        expectOp(")");
        while (isWord("const") || isWord("override") || isWord("final") || isWord("noexcept")) pos++;
        if (isOp("->")) { pos++; tryType(); }  // trailing return type
        if (isOp("=")) { pos++; pos++; expectOp(";"); return f; }  // = 0 / = default / = delete
        if (isOp(";")) { pos++; return f; }                        // prototype
        Node* init = nullptr;
        if (ctor && isOp(":")) {
            // member initialiser list: Foo() : a(1), b{2} { … }
            pos++;
            init = prog->node(N::Block, line());
            while (true) {
                int ln = line();
                std::string m = expectIdent();
                Node* target = prog->node(N::Ident, ln);
                target->op = m; target->id = prog->intern(m);
                Node* value;
                if (isOp("{")) {
                    Node* l = parseInitList();
                    value = l->c.size() == 1 ? l->c[0] : l;
                } else {
                    expectOp("(");
                    value = isOp(")") ? prog->node(N::Nil, ln) : parseExpr();
                    expectOp(")");
                }
                Node* as = prog->node(N::Assign, ln);
                as->op = "=";
                as->c = {target, value};
                Node* st = prog->node(N::ExprStmt, ln);
                st->c.push_back(as);
                init->c.push_back(st);
                if (isOp(",")) { pos++; continue; }
                break;
            }
        }
        f->body = parseBlock();
        if (init) { init->c.push_back(f->body); f->body = init; }
        return f;
    }

    void parseTopLevelDecl() {
        std::string type = tryType();
        if (type.empty()) error("expected a declaration (class, function or variable)");
        int ln = line();
        std::string name = expectIdent();
        std::string owner;
        while (isOp("::") && peek(1).k == Tok::Ident) { owner = owner.empty() ? name : owner + "::" + name; name = peek(1).s; pos += 2; }
        if (isOp("~") ) error("destructors must be declared inside the class");
        if (isOp("(")) {
            if (!owner.empty()) {
                // out-of-class member definition: void Player::Update(float dt) { … }
                auto it = prog->classes.find(owner);
                if (it == prog->classes.end()) error("unknown class '" + owner + "'");
                bool ctor = name == owner;
                FuncDef* f = parseFunctionRest(ctor ? "__ctor" : name, type, ctor);
                if (f->body) it->second->methods[ctor ? "__ctor" : name] = f;
                return;
            }
            FuncDef* f = parseFunctionRest(name, type);
            if (f->body) prog->functions[name] = f;
            return;
        }
        if (!owner.empty()) {
            // static member definition: int Game::score = 0;
            Node* d = prog->node(N::Decl, ln);
            d->op = type; d->id = prog->intern(owner + "::" + name);
            if (isOp("=")) { pos++; d->c.push_back(parseInitializer()); }
            expectOp(";");
            prog->globalDecls.push_back(d);
            return;
        }
        pos--;  // back to the name
        Node* g = parseDeclList(type);
        expectOp(";");
        if (g->k == N::DeclGroup) for (Node* d : g->c) prog->globalDecls.push_back(d);
        else prog->globalDecls.push_back(g);
    }

    /** name [= init] {, name [= init]} — cursor on the first name. */
    Node* parseDeclList(const std::string& type) {
        std::vector<Node*> decls;
        while (true) {
            int ln = line();
            while (isOp("*") || isOp("&")) pos++;
            std::string name = expectIdent();
            Node* d = prog->node(N::Decl, ln);
            d->op = type;
            d->id = prog->intern(name);
            bool isArray = false;
            Node* arrSize = nullptr;
            while (isOp("[")) { pos++; if (!isOp("]")) arrSize = parseExpr(); expectOp("]"); isArray = true; }
            if (isArray) {
                d->op = "List";
                d->num = 1;  // marks a C array (sized with default elements)
                d->isInt = type == "int";
                if (arrSize) { Node* sz = arrSize; Node* holder = prog->node(N::Construct, ln); holder->op = "__array:" + type; holder->c.push_back(sz); d->c.push_back(holder); }
            }
            if (isOp("=")) { pos++; Node* init = parseInitializer(); d->c.clear(); d->c.push_back(init); }
            else if (isOp("{")) { Node* init = parseInitList(); d->c.clear(); d->c.push_back(init); }
            else if (isOp("(") && !isArray) d->c.push_back(parseCtorArgs(type));
            decls.push_back(d);
            if (isOp(",")) { pos++; continue; }
            break;
        }
        if (decls.size() == 1) return decls[0];
        Node* g = prog->node(N::DeclGroup, decls[0]->line);
        g->c = decls;
        return g;
    }

    // ------------------------------------------------------------ statements
    Node* parseBlock() {
        Node* b = prog->node(N::Block, line());
        expectOp("{");
        while (!isOp("}")) {
            if (at(Tok::End)) error("missing '}'");
            b->c.push_back(parseStatement());
        }
        expectOp("}");
        return b;
    }

    Node* parseStatement() {
        int ln = line();
        if (isOp("{")) return parseBlock();
        if (isOp(";")) { pos++; return prog->node(N::Empty, ln); }
        if (at(Tok::Ident)) {
            const std::string& w = cur().s;
            if (w == "if") {
                pos++;
                if (isWord("constexpr")) pos++;
                expectOp("(");
                Node* n = prog->node(N::If, ln);
                n->c.push_back(parseCondition());
                expectOp(")");
                n->c.push_back(parseStatement());
                if (isWord("else")) { pos++; n->c.push_back(parseStatement()); }
                return n;
            }
            if (w == "while") {
                pos++;
                expectOp("(");
                Node* n = prog->node(N::While, ln);
                n->c.push_back(parseCondition());
                expectOp(")");
                n->c.push_back(parseStatement());
                return n;
            }
            if (w == "do") {
                pos++;
                Node* n = prog->node(N::DoWhile, ln);
                n->c.push_back(parseStatement());
                if (!isWord("while")) error("expected 'while' after do-block");
                pos++;
                expectOp("(");
                n->c.push_back(parseExpr());
                expectOp(")");
                expectOp(";");
                return n;
            }
            if (w == "for") return parseFor();
            if (w == "return") {
                pos++;
                Node* n = prog->node(N::Return, ln);
                if (!isOp(";")) n->c.push_back(isOp("{") ? parseInitList() : parseExpr());
                expectOp(";");
                return n;
            }
            if (w == "break") { pos++; expectOp(";"); return prog->node(N::Break, ln); }
            if (w == "continue") { pos++; expectOp(";"); return prog->node(N::Continue, ln); }
            if (w == "switch") return parseSwitch();
            if (w == "delete") { skipTo(";"); return prog->node(N::Empty, ln); }
            if (w == "goto") error("goto is not supported");
            if (w == "try") error("exceptions (try/catch) are not supported in scripts");
            if (w == "enum") { parseEnum(); return prog->node(N::Empty, ln); }
            if (declAhead()) {
                std::string type = tryType();
                Node* d = parseDeclList(type);
                expectOp(";");
                return d;
            }
        }
        Node* n = prog->node(N::ExprStmt, ln);
        n->c.push_back(parseExpr());
        expectOp(";");
        return n;
    }

    /** if/while conditions may declare a variable: if (auto e = Find("x")) */
    Node* parseCondition() {
        if (declAhead()) {
            int ln = line();
            std::string type = tryType();
            std::string name = expectIdent();
            expectOp("=");
            Node* d = prog->node(N::Decl, ln);
            d->op = type; d->id = prog->intern(name);
            d->c.push_back(parseAssign());
            // a DeclGroup holding one decl evaluates to the declared value inside conditions
            Node* g = prog->node(N::DeclGroup, ln);
            g->c.push_back(d);
            g->op = "cond";
            return g;
        }
        return parseExpr();
    }

    Node* parseFor() {
        int ln = line();
        pos++;
        expectOp("(");
        // range-for: for (auto& e : list)
        {
            size_t save = pos;
            std::string type = tryType();
            if (!type.empty() && at(Tok::Ident) && peekOp(1, ":")) {
                Node* n = prog->node(N::ForRange, ln);
                n->op = type;
                n->id = prog->intern(toks[pos].s);
                pos += 2;
                n->c.push_back(parseExpr());
                expectOp(")");
                n->c.push_back(parseStatement());
                return n;
            }
            pos = save;
        }
        Node* n = prog->node(N::For, ln);
        Node* init;
        if (isOp(";")) { init = prog->node(N::Empty, ln); pos++; }
        else if (declAhead()) { std::string type = tryType(); init = parseDeclList(type); expectOp(";"); }
        else { init = prog->node(N::ExprStmt, ln); init->c.push_back(parseExpr()); expectOp(";"); }
        Node* cond = isOp(";") ? prog->node(N::Empty, ln) : parseExpr();
        expectOp(";");
        Node* step = isOp(")") ? prog->node(N::Empty, ln) : parseExpr();
        expectOp(")");
        n->c = {init, cond, step, parseStatement()};
        return n;
    }

    Node* parseSwitch() {
        int ln = line();
        pos++;
        expectOp("(");
        Node* n = prog->node(N::Switch, ln);
        n->c.push_back(parseExpr());
        expectOp(")");
        expectOp("{");
        Node* current = nullptr;
        while (!isOp("}")) {
            if (at(Tok::End)) error("missing '}' in switch");
            if (isWord("case")) {
                pos++;
                current = prog->node(N::Case, line());
                current->c.push_back(parseTernary());
                expectOp(":");
                n->c.push_back(current);
                continue;
            }
            if (isWord("default")) {
                pos++;
                expectOp(":");
                current = prog->node(N::Case, line());
                current->op = "default";
                current->c.push_back(prog->node(N::Empty, line()));
                n->c.push_back(current);
                continue;
            }
            if (!current) error("statement before the first case");
            current->c.push_back(parseStatement());
        }
        expectOp("}");
        return n;
    }

    // ------------------------------------------------------------ expressions
    Node* parseExpr() {
        Node* e = parseAssign();
        while (isOp(",")) {
            // comma operator (mostly in for-loops): evaluate left, result is right
            int ln = line();
            pos++;
            Node* r = parseAssign();
            Node* b = prog->node(N::Binary, ln);
            b->op = ",";
            b->c = {e, r};
            e = b;
        }
        return e;
    }

    Node* parseAssign() {
        Node* lhs = parseTernary();
        if (cur().k == Tok::Op) {
            const std::string& o = cur().s;
            if (o == "=" || o == "+=" || o == "-=" || o == "*=" || o == "/=" || o == "%=" || o == "&=" || o == "|=" ||
                o == "^=" || o == "<<=" || o == ">>=") {
                int ln = line();
                std::string op = o;
                pos++;
                Node* rhs = isOp("{") ? parseInitList() : parseAssign();
                Node* n = prog->node(N::Assign, ln);
                n->op = op;
                n->c = {lhs, rhs};
                return n;
            }
        }
        return lhs;
    }

    Node* parseTernary() {
        Node* c = parseBinary(1);
        if (isOp("?")) {
            int ln = line();
            pos++;
            Node* a = parseAssign();
            expectOp(":");
            Node* b = parseAssign();
            Node* n = prog->node(N::Ternary, ln);
            n->c = {c, a, b};
            return n;
        }
        return c;
    }

    static int prec(const Token& t) {
        if (t.k != Tok::Op) {
            if (t.k == Tok::Ident) {
                if (t.s == "or") return 1;
                if (t.s == "and") return 2;
            }
            return 0;
        }
        const std::string& o = t.s;
        if (o == "||") return 1;
        if (o == "&&") return 2;
        if (o == "|") return 3;
        if (o == "^") return 4;
        if (o == "&") return 5;
        if (o == "==" || o == "!=") return 6;
        if (o == "<" || o == ">" || o == "<=" || o == ">=") return 7;
        if (o == "<<" || o == ">>") return 8;
        if (o == "+" || o == "-") return 9;
        if (o == "*" || o == "/" || o == "%") return 10;
        return 0;
    }

    Node* parseBinary(int minPrec) {
        Node* lhs = parseUnary();
        while (true) {
            int p = prec(cur());
            if (p == 0 || p < minPrec) break;
            std::string op = cur().s;
            if (op == "or") op = "||";
            if (op == "and") op = "&&";
            int ln = line();
            pos++;
            Node* rhs = parseBinary(p + 1);
            Node* n = prog->node(op == "&&" ? N::And : op == "||" ? N::Or : N::Binary, ln);
            n->op = op;
            n->c = {lhs, rhs};
            lhs = n;
        }
        return lhs;
    }

    Node* parseUnary() {
        int ln = line();
        if (cur().k == Tok::Op) {
            const std::string& o = cur().s;
            if (o == "-" || o == "+" || o == "!" || o == "~" || o == "*" || o == "&") {
                std::string op = o;
                pos++;
                Node* n = prog->node(N::Unary, ln);
                n->op = op;
                n->c.push_back(parseUnary());
                return n;
            }
            if (o == "++" || o == "--") {
                std::string op = o;
                pos++;
                Node* n = prog->node(N::PreInc, ln);
                n->op = op;
                n->c.push_back(parseUnary());
                return n;
            }
            if (o == "(") {
                // C-style cast: (float)x
                size_t save = pos;
                pos++;
                std::string t = tryType();
                if (!t.empty() && isOp(")")) {
                    pos++;
                    if (!(isOp(")") || isOp(";") || isOp(",") || cur().k == Tok::End)) {
                        Node* n = prog->node(N::Cast, ln);
                        n->op = t;
                        n->c.push_back(parseUnary());
                        return n;
                    }
                }
                pos = save;
            }
        }
        if (isWord("not")) { pos++; Node* n = prog->node(N::Unary, ln); n->op = "!"; n->c.push_back(parseUnary()); return n; }
        if (isWord("sizeof")) error("sizeof is not supported in scripts");
        return parsePostfix(parsePrimary());
    }

    Node* parsePostfix(Node* e) {
        while (true) {
            int ln = line();
            if (isOp("(")) {
                pos++;
                Node* n = prog->node(N::Call, ln);
                n->c.push_back(e);
                while (!isOp(")")) {
                    n->c.push_back(isOp("{") ? parseInitList() : parseAssign());
                    if (isOp(",")) pos++;
                    else break;
                }
                expectOp(")");
                e = n;
            } else if (isOp(".") || isOp("->")) {
                pos++;
                Node* n = prog->node(N::Member, ln);
                n->op = expectIdent();
                if (isOp("<") && (n->op == "GetComponent" || n->op == "get" || n->op == "Cast")) skipAngles();
                n->c.push_back(e);
                e = n;
            } else if (isOp("[")) {
                pos++;
                Node* n = prog->node(N::Index, ln);
                n->c.push_back(e);
                n->c.push_back(parseExpr());
                expectOp("]");
                e = n;
            } else if (isOp("++") || isOp("--")) {
                Node* n = prog->node(N::PostInc, ln);
                n->op = cur().s;
                pos++;
                n->c.push_back(e);
                e = n;
            } else break;
        }
        return e;
    }

    Node* parsePrimary() {
        int ln = line();
        const Token& t = cur();
        switch (t.k) {
            case Tok::Int:
            case Tok::Float: {
                Node* n = prog->node(N::Num, ln);
                n->num = t.num;
                n->isInt = t.k == Tok::Int;
                pos++;
                return n;
            }
            case Tok::Char:
            case Tok::Str: {
                Node* n = prog->node(N::Str, ln);
                n->op = t.s;
                pos++;
                return n;
            }
            case Tok::Op: {
                if (t.s == "(") {
                    pos++;
                    Node* e = parseExpr();
                    expectOp(")");
                    return e;
                }
                if (t.s == "{") return parseInitList();
                if (t.s == "[") error("lambdas are not supported yet — use a member function");
                if (t.s == "::") { pos++; return parsePrimary(); }
                error("unexpected symbol");
            }
            case Tok::End: error("unexpected end of file");
            case Tok::Ident: break;
        }
        std::string w = t.s;
        pos++;
        if (w == "true" || w == "false") { Node* n = prog->node(N::Bool, ln); n->num = w == "true" ? 1 : 0; return n; }
        if (w == "nullptr" || w == "NULL" || w == "nil") return prog->node(N::Nil, ln);
        if (w == "this") return prog->node(N::This, ln);
        if (w == "static_cast" || w == "dynamic_cast" || w == "reinterpret_cast" || w == "const_cast" || w == "Cast") {
            std::string type = "auto";
            if (isOp("<")) {
                pos++;
                type = tryType();
                if (type.empty()) error("expected a type in cast");
                expectCloseAngle();
            }
            expectOp("(");
            Node* n = prog->node(N::Cast, ln);
            n->op = type;
            n->c.push_back(parseExpr());
            expectOp(")");
            return n;
        }
        if (w == "new") {
            std::string type = tryType();
            if (type.empty()) error("expected a type after new");
            Node* n = prog->node(N::New, ln);
            n->op = type;
            if (isOp("(")) {
                pos++;
                while (!isOp(")")) { n->c.push_back(parseAssign()); if (isOp(",")) pos++; else break; }
                expectOp(")");
            } else if (isOp("{")) {
                Node* l = parseInitList();
                n->c = l->c;
            }
            return n;
        }
        if (w == "TEXT" && isOp("(")) {  // UE string macro
            pos++;
            Node* e = parseExpr();
            expectOp(")");
            return e;
        }
        std::string name = w;
        while (isOp("::") && peek(1).k == Tok::Ident) { name += "::" + peek(1).s; pos += 2; }
        if (name.rfind("std::", 0) == 0) name = name.substr(5);
        // templated type in expression context: std::vector<int>{…}, Vec3{1,2,3}
        if (isOp("<") && isTypeName(name) && !classNames.count(name)) {
            size_t save = pos;
            skipAngles();
            if (!(isOp("(") || isOp("{"))) pos = save;
        }
        if (isOp("{") && isTypeName(name)) {
            Node* l = parseInitList();
            Node* n = prog->node(N::Construct, ln);
            n->op = classNames.count(name) ? name : normalizeType(name);
            n->isInt = true;  // brace initialisation: children are elements, not constructor sizes
            n->c = l->c;
            return n;
        }
        Node* n = prog->node(N::Ident, ln);
        n->op = name;
        n->id = prog->intern(name);
        return n;
    }
};

}  // namespace

char typeKind(const std::string& t) {
    if (t == "int") return 'i';
    if (t == "float") return 'f';
    if (t == "bool") return 'b';
    return 'a';
}

ClassDef* Program::behaviourClass() const {
    for (const std::string& n : classOrder) {
        auto it = classes.find(n);
        if (it != classes.end() && it->second->isBehaviour) return it->second;
    }
    // no class derives from Behaviour: the last class with an Update/Start method, else the first class
    for (auto it = classOrder.rbegin(); it != classOrder.rend(); ++it) {
        ClassDef* c = classes.at(*it);
        for (const char* m : {"Update", "Start", "update", "start", "Tick", "BeginPlay"})
            if (c->methods.count(m)) return c;
    }
    return classOrder.empty() ? nullptr : classes.at(classOrder.front());
}

std::unique_ptr<Program> compile(const std::string& name, const std::string& src) {
    auto prog = std::make_unique<Program>();
    prog->name = name;
    std::vector<Token> toks = lex(src);
    Parser p(toks, prog.get());
    p.parseProgram();
    // link base classes: inherited fields first, methods resolved through the chain at call time
    static const std::unordered_set<std::string> BEHAVIOURS = {
        "Behaviour", "Behavior", "MonoBehaviour", "Script", "Component", "ActorComponent", "UActorComponent", "AActor",
        "Actor", "APawn", "ACharacter", "GameScript", "NativeScript", "ScriptBehaviour"};
    std::unordered_set<ClassDef*> done;
    std::function<void(ClassDef*, int)> link = [&](ClassDef* c, int guard) {
        if (done.count(c)) return;
        if (guard > 32) throw ScriptError("class hierarchy of '" + c->name + "' is circular", 0);
        std::vector<FieldDef> own = c->fields;
        c->fields.clear();
        if (!c->base.empty()) {
            auto it = prog->classes.find(c->base);
            if (it != prog->classes.end()) {
                link(it->second, guard + 1);
                c->baseClass = it->second;
                c->isBehaviour = it->second->isBehaviour;
                c->fields = it->second->fields;
            } else if (BEHAVIOURS.count(c->base)) c->isBehaviour = true;
        }
        for (auto& f : own) c->fields.push_back(f);
        c->fieldIndex.clear();
        for (size_t i = 0; i < c->fields.size(); i++) { c->fieldIndex[c->fields[i].id] = (int) i; c->fields[i].kind = typeKind(c->fields[i].type); }  // later (own) fields shadow inherited
        c->statics.assign(c->fields.size(), Value());
        done.insert(c);
    };
    for (const std::string& n : prog->classOrder) link(prog->classes[n], 0);
    return prog;
}

}  // namespace sengine
