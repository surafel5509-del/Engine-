package com.sengine.engine.script

import java.util.ArrayDeque

object GdScriptTranspiler {
    fun transpile(code: String): String {
        val lines = code.lines()
        val sb = StringBuilder()
        val indentStack = ArrayDeque<Int>()
        indentStack.push(0)

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) { sb.append("\n"); continue }

            val indent = line.takeWhile { it.isWhitespace() }.length
            var content = line.trimStart()

            if (content.startsWith("#")) {
                sb.append(" ".repeat(indent)).append("//").append(content.substring(1)).append("\n")
                continue
            }

            while (indentStack.peek() != null && indentStack.peek()!! > indent) {
                indentStack.pop()
                val closingIndent = indentStack.peek() ?: 0
                sb.append(" ".repeat(closingIndent)).append("}\n")
            }

            // Replace GDScript keywords
            if (content.startsWith("extends ")) {
                sb.append("// ").append(content).append("\n")
                continue
            }

            // Function declarations
            if (content.startsWith("func ")) {
                content = content.removePrefix("func ").trim()
                val isProcess = content.contains("_process") || content.contains("_physics_process")
                content = content.replace("_ready()", "start()")
                    .replace("_process(delta)", "update(dt)")
                    .replace("_process(dt)", "update(dt)")
                    .replace("_physics_process(delta)", "update(dt)")
                    .replace("_physics_process(dt)", "update(dt)")
                    .replace("_on_body_entered(", "onTrigger(")
                content = "function " + content
                if (isProcess && content.endsWith(":")) {
                    content = content.dropLast(1).trimEnd() + " {\n" + " ".repeat(indent + 4) + "var delta = dt;"
                    sb.append(" ".repeat(indent)).append(content).append("\n")
                    indentStack.push(indent + 4)
                    continue
                }
            }

            // elif -> else if
            if (content.startsWith("elif ")) {
                content = "else if " + content.removePrefix("elif ").trim()
            }

            // Wrap control statement conditions in parentheses if needed: if x > 0: -> if (x > 0):
            if (content.startsWith("if ") || content.startsWith("else if ") || content.startsWith("while ")) {
                val keyword = when {
                    content.startsWith("else if ") -> "else if"
                    content.startsWith("if ") -> "if"
                    else -> "while"
                }
                var body = content.removePrefix(keyword).trim()
                val hasColonEnd = body.endsWith(":")
                if (hasColonEnd) body = body.dropLast(1).trimEnd()
                if (!body.startsWith("(")) {
                    body = "($body)"
                }
                content = "$keyword $body" + if (hasColonEnd) ":" else ""
            }

            // Variable declarations
            if (content.startsWith("var ")) {
                content = content.replace(Regex(": \\w+"), "")
            }

            // Replace print with log
            content = content.replace(Regex("\\bprint\\("), "log(")

            // Convert operators
            content = content.replace(Regex("\\band\\b"), "&&")
                .replace(Regex("\\bor\\b"), "||")
                .replace(Regex("\\bnot\\b"), "!")

            val hasColon = content.endsWith(":")
            if (hasColon) {
                content = content.dropLast(1).trimEnd() + " {"
                sb.append(" ".repeat(indent)).append(content).append("\n")
                indentStack.push(indent + 4)
            } else {
                if (!content.endsWith(";") && !content.endsWith("{") && !content.endsWith("}")) {
                    content += ";"
                }
                sb.append(" ".repeat(indent)).append(content).append("\n")
            }
        }

        while (indentStack.size > 1) {
            indentStack.pop()
            val closingIndent = indentStack.peek() ?: 0
            sb.append(" ".repeat(closingIndent)).append("}\n")
        }

        return sb.toString()
    }
}

object LuaScriptTranspiler {
    fun transpile(code: String): String {
        val lines = code.lines()
        val sb = StringBuilder()

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) { sb.append("\n"); continue }
            val indent = line.takeWhile { it.isWhitespace() }.length
            var content = line.trimStart()

            if (content.startsWith("--")) {
                sb.append(" ".repeat(indent)).append("//").append(content.substring(2)).append("\n")
                continue
            }

            // Keywords replacement
            content = content.replace(Regex("\\blocal\\b"), "var")
                .replace(Regex("\\bnil\\b"), "null")
                .replace(Regex("\\bprint\\("), "log(")
                .replace("~=", "!=")
                .replace(Regex("\\bnot\\b"), "!")
                .replace(Regex("\\band\\b"), "&&")
                .replace(Regex("\\bor\\b"), "||")

            // Method call obj:method(...) -> obj.method(...)
            content = content.replace(Regex("(\\b\\w+):(\\w+)\\("), "$1.$2(")

            // Function headers in Lua
            if (content.startsWith("function ") || content.startsWith("var function ")) {
                if (!content.endsWith("{")) {
                    content += " {"
                }
            } else if (content.contains(Regex("\\belseif\\b"))) {
                content = content.replace("elseif", "} else if (")
                    .replace("then", ") {")
            } else if (content.contains(Regex("\\bif\\b")) && content.contains("then")) {
                content = content.replace("if", "if (")
                    .replace("then", ") {")
            } else if (content.startsWith("else")) {
                content = "} else {"
            } else if (content == "end") {
                content = "}"
            }

            if (!content.endsWith(";") && !content.endsWith("{") && !content.endsWith("}")) {
                content += ";"
            }

            sb.append(" ".repeat(indent)).append(content).append("\n")
        }

        return sb.toString()
    }
}
