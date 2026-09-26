#include "SScript.h"

#include <cctype>
#include <cstdlib>
#include <cstring>

namespace sengine {

static const char* OPS[] = {
    "<<=", ">>=", "...", "->", "::", "++", "--", "+=", "-=", "*=", "/=", "%=", "==", "!=", "<=", ">=", "&&", "||",
    "<<", ">>", "&=", "|=", "^=", nullptr};

std::vector<Token> lex(const std::string& src) {
    std::vector<Token> out;
    size_t i = 0, n = src.size();
    int line = 1;
    bool lineStart = true;
    while (i < n) {
        char c = src[i];
        if (c == '\n') { line++; i++; lineStart = true; continue; }
        if (isspace((unsigned char) c)) { i++; continue; }
        // preprocessor lines (#include, #define, #pragma) are ignored
        if (c == '#' && lineStart) {
            while (i < n && src[i] != '\n') {
                if (src[i] == '\\' && i + 1 < n && src[i + 1] == '\n') { i++; line++; }
                i++;
            }
            continue;
        }
        lineStart = false;
        if (c == '/' && i + 1 < n && src[i + 1] == '/') { while (i < n && src[i] != '\n') i++; continue; }
        if (c == '/' && i + 1 < n && src[i + 1] == '*') {
            i += 2;
            while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) { if (src[i] == '\n') line++; i++; }
            if (i + 1 >= n) throw ScriptError("unterminated /* comment", line);
            i += 2;
            continue;
        }
        Token t;
        t.line = line;
        if (isalpha((unsigned char) c) || c == '_') {
            size_t s = i;
            while (i < n && (isalnum((unsigned char) src[i]) || src[i] == '_')) i++;
            t.k = Tok::Ident;
            t.s = src.substr(s, i - s);
            out.push_back(t);
            continue;
        }
        if (isdigit((unsigned char) c) || (c == '.' && i + 1 < n && isdigit((unsigned char) src[i + 1]))) {
            size_t s = i;
            bool isFloat = false;
            if (c == '0' && i + 1 < n && (src[i + 1] == 'x' || src[i + 1] == 'X')) {
                i += 2;
                while (i < n && isxdigit((unsigned char) src[i])) i++;
                t.k = Tok::Int;
                t.num = (double) strtoll(src.substr(s, i - s).c_str(), nullptr, 16);
            } else {
                while (i < n && (isdigit((unsigned char) src[i]) || src[i] == '.' || src[i] == '\'')) { if (src[i] == '.') isFloat = true; i++; }
                if (i < n && (src[i] == 'e' || src[i] == 'E')) {
                    isFloat = true; i++;
                    if (i < n && (src[i] == '+' || src[i] == '-')) i++;
                    while (i < n && isdigit((unsigned char) src[i])) i++;
                }
                std::string lit;
                for (size_t k = s; k < i; k++) if (src[k] != '\'') lit += src[k];
                t.num = strtod(lit.c_str(), nullptr);
                t.k = isFloat ? Tok::Float : Tok::Int;
            }
            // suffixes: 1.0f, 10u, 5L, 2.0d
            while (i < n && (src[i] == 'f' || src[i] == 'F' || src[i] == 'u' || src[i] == 'U' || src[i] == 'l' || src[i] == 'L' || src[i] == 'd')) {
                if (src[i] == 'f' || src[i] == 'F' || src[i] == 'd') t.k = Tok::Float;
                i++;
            }
            out.push_back(t);
            continue;
        }
        if (c == '"' || c == '\'') {
            char q = c;
            i++;
            std::string s;
            while (i < n && src[i] != q) {
                char d = src[i];
                if (d == '\n') throw ScriptError("unterminated string", line);
                if (d == '\\' && i + 1 < n) {
                    i++;
                    char e = src[i];
                    switch (e) {
                        case 'n': s += '\n'; break;
                        case 't': s += '\t'; break;
                        case 'r': s += '\r'; break;
                        case '0': s += '\0'; break;
                        case '\\': s += '\\'; break;
                        case '"': s += '"'; break;
                        case '\'': s += '\''; break;
                        default: s += e;
                    }
                } else s += d;
                i++;
            }
            if (i >= n) throw ScriptError("unterminated string", line);
            i++;
            if (q == '\'') { t.k = Tok::Char; t.num = s.empty() ? 0 : (unsigned char) s[0]; t.s = s; }
            else {
                t.k = Tok::Str; t.s = s;
                // adjacent literals concatenate: "a" "b"
                if (!out.empty() && out.back().k == Tok::Str) { out.back().s += s; continue; }
            }
            out.push_back(t);
            continue;
        }
        t.k = Tok::Op;
        bool matched = false;
        for (int k = 0; OPS[k]; k++) {
            size_t len = strlen(OPS[k]);
            if (src.compare(i, len, OPS[k]) == 0) { t.s = OPS[k]; i += len; matched = true; break; }
        }
        if (!matched) {
            if (strchr("+-*/%=<>!&|^~?:;,.()[]{}", c) == nullptr)
                throw ScriptError(std::string("unexpected character '") + c + "'", line);
            t.s = std::string(1, c);
            i++;
        }
        out.push_back(t);
    }
    Token e; e.k = Tok::End; e.line = line;
    out.push_back(e);
    return out;
}

}  // namespace sengine
