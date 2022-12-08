package com.stepanov.bbf.bugfinder.generator.antrlgenerator

import org.antlr.v4.tool.ast.GrammarAST
import java.util.*

class Code {
    var code: String = ""

    fun append(appendedCode: String) {
        code += " $appendedCode"
    }

    fun appendWithoutSpace(appendedCode: String) {
        code += appendedCode
    }
}

enum class NodeType(val tokenId: Int) {
    TOKEN(66),
    PRODUCTION(57),
    BLOCK(78),
    ALT(74),
    SET(98),
    LITERAL(62),
    RULE_MODIFIERS(96),
    AT_LEAST_ONCE(90), // +
    UNKNOWN(80), // *
    AT_MOST_ONCE(89), // ?
    LEXER_ALT_ACTION(87);

    companion object {
        private val map = NodeType.values().associateBy(NodeType::tokenId)
        fun fromValue(tokenId: Int): NodeType? = map[tokenId]
    }
}

fun randInt(min: Int, max: Int): Int {
    val rand = Random()
    return rand.nextInt(max - min + 1) + min
}

class CodeGen(
    private val lexerRules: GrammarRulesMap,
    private val parserRules: GrammarRulesMap,
    private val baseRule: GrammarAST,
    private val maxDepth: Int
) {
    private val code = Code()
    fun gen(ast: GrammarAST = baseRule, depth: Int = 1): String {
        if (ast.children != null) {
            val alternatives = mutableListOf<GrammarAST>()
            ast.children.forEach {
                processNode(it as GrammarAST, alternatives, depth)
            }

            if (alternatives.isNotEmpty()) {
                gen(alternatives.random(), depth + 1)
            }
        }

        return code.code.replace("EOF", "")
    }

    fun processNode(node: GrammarAST, alternatives: MutableList<GrammarAST>, depth: Int) {
        val type = NodeType.fromValue(node.type)

        when (type) {
            NodeType.BLOCK, NodeType.LEXER_ALT_ACTION -> {
                gen(node, depth + 1)
//            processNode(node.children[0] as GrammarAST, mutableListOf(), depth)
            }
            NodeType.RULE_MODIFIERS -> {
                gen(node, depth + 1)
            }
            NodeType.ALT -> {
                alternatives.add(node)
            }
            NodeType.SET -> {
                processNode(node.children.random() as GrammarAST, mutableListOf(), depth)
            }
            NodeType.AT_MOST_ONCE -> {
                var i = 0
                while (i < randInt(0, 1) && depth <= maxDepth) {
                    gen(node, depth + 1)
                    i++
                }
            }
            NodeType.AT_LEAST_ONCE -> {
                var i = 0
                while (i < randInt(1, 3)) {
                    gen(node, depth + 1)
                    i++
                }
            }
            NodeType.UNKNOWN -> {
                var i = 0
                while (i < randInt(0, 2) && depth <= maxDepth) {
                    gen(node, depth + 1)
                    i++
                }
            }
            NodeType.PRODUCTION -> {
                gen(parserRules[node.text]!!, depth + 1)
            }
            NodeType.LITERAL -> {
                val literal = node.text.substring(1, node.text.length - 1)
                val value: String

                if (literal == "\\n") {
                    value = "\n"
                } else if (literal == "\\r") {
                    value = "\r"
                } else {
                    value = literal
                }

                code.append(value)
            }
            NodeType.TOKEN -> {
                if (node.text == "Identifier") {
                    code.append("foobar")
                }
                if (node.text in lexerRules) {
                    gen(lexerRules[node.text]!!, depth + 1)
                } else {
                    code.append(node.text)
                }
            }
        }
    }
}