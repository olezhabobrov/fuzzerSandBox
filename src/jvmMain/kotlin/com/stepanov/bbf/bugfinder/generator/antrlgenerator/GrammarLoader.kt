package com.stepanov.bbf.bugfinder.generator.antrlgenerator

import org.antlr.v4.tool.Grammar
import org.antlr.v4.tool.LexerGrammar
import java.io.File

data class GrammarSet(
        val lexer: LexerGrammar,
        val parser: Grammar
)

fun loadGrammar(lexerGrammarPath: String, parserGrammarPath: String) =
        GrammarSet(
                LexerGrammar(File(lexerGrammarPath).readText()),
                Grammar(File(parserGrammarPath).readText())
        )
