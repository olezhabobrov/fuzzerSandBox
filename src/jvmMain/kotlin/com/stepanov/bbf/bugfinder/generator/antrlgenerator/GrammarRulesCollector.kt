package com.stepanov.bbf.bugfinder.generator.antrlgenerator

import org.antlr.v4.tool.Grammar
import org.antlr.v4.tool.LexerGrammar
import org.antlr.v4.tool.ast.GrammarAST

typealias GrammarRulesMap = MutableMap<String, GrammarAST>

class GrammarRulesCollector(lexer: LexerGrammar, private val parser: Grammar) {
    companion object {
        private const val TOP_LEVEL_RULE = "kotlinFile"
    }

    private val lexerRules: GrammarRulesMap = mutableMapOf()
    private val parserRules: GrammarRulesMap = mutableMapOf()

    init {
        lexer.rules.forEach {
            processLexerRule(it.value.ast)
        }

        parser.rules.forEach {
            processParserRule(it.value.ast)
        }
    }

    private fun processLexerRule(ast: GrammarAST, level: Int = 0) {
        if (ast.children == null) return

        var currentRule: String? = null

        ast.children.forEach { node ->
            node as GrammarAST

            val type = NodeType.fromValue(node.type)

            if (currentRule != null) {
                lexerRules[currentRule!!] = node
                currentRule = null
            }

            if (level == 0 && type == NodeType.TOKEN) {
                currentRule = node.text
                return@forEach
            }

            if (type != null) {
                processLexerRule(node, level + 1)
            } else {
                println(node.text)
            }
        }
    }

    private fun processParserRule(ast: GrammarAST, level: Int = 0) {
        if (ast.children != null) {
            var currentRule: String? = null

            ast.children.forEach {
                it as GrammarAST

                val type = NodeType.fromValue(it.type)

                if (level == 0 && type == NodeType.PRODUCTION) {
                    currentRule = it.text
                }

                if (it.text !in parserRules && currentRule != null) {
                    parserRules[currentRule!!] = it
                }

                if (type != null) {
                    processParserRule(it, level + 1)
                }
            }
        }
    }

    fun getBaseRule(rule: String = TOP_LEVEL_RULE): GrammarAST {
        return parser.getRule(rule).ast.children[1] as GrammarAST
    }

    fun getRules(): Pair<GrammarRulesMap, GrammarRulesMap> {
        return Pair(lexerRules, parserRules)
    }
}