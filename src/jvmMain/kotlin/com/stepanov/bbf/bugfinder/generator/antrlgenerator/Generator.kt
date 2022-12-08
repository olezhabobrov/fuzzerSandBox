package com.stepanov.bbf.bugfinder.generator.antrlgenerator

import com.stepanov.bbf.reduktor.util.getAllWithout
import org.antlr.runtime.tree.CommonTree
import org.antlr.v4.tool.Grammar
import org.antlr.v4.tool.LexerGrammar
import org.antlr.v4.tool.ast.*
import java.io.File

class Generator() {

    private val rules: Pair<GrammarRulesMap, GrammarRulesMap>
    private val rulesCollector: GrammarRulesCollector

    init {
        val lexer = LexerGrammar(File("tmp/grammar/KotlinLexer.g4").readText())
        val parser = Grammar(File("tmp/grammar/KotlinParser.g4").readText())
        rulesCollector = GrammarRulesCollector(lexer, parser)
        rules = rulesCollector.getRules()
    }

    fun generate(): String {
        val (lexer, _) = loadGrammar(
            lexerGrammarPath = "tmp/grammar/KotlinLexer.g4",
            parserGrammarPath = "tmp/grammar/KotlinParser.g4"
        )

        val codegen = CodeGen(rules.first, rules.second, rulesCollector.getBaseRule("kotlinFile"), maxDepth = 30)
        val code = codegen.gen()
        val tokens = (lexer.ast.children[2] as GrammarAST).children.map { it as RuleAST }
        val tokensToValue = tokens.map { it.children.first().toString() to getFirstTerminalASTNode(it.children.last() as BlockAST) }
        val myCode = code.split(" ")
            .getAllWithout(0)
            .map { tokensToValue.toMap()[it] }.mapNotNull { it?.text }
            .map { if (it.contains("'\\n'")) "\n" else it.substringAfter('\'').substringBefore('\'') }
        return myCode.joinToString("")
    }

    private fun getFirstTerminalASTNode(node: CommonTree): TerminalAST? {
        node.children?.find { it is TerminalAST }?.let { return it as TerminalAST }
        if (node.children == null) return null
        for (ch in node.children) {
            return getFirstTerminalASTNode(ch as CommonTree)
        }
        return null
    }
    /* (node.children.find { it is TerminalAST } ?: node.children?.find { getFirstTerminalASTNode(it as CommonTree) is TerminalAST }) as TerminalAST?*/
    /*node.children.map { getFirstTerminalASTNode(it as CommonTree) }*/


}