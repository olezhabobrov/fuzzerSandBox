package com.stepanov.bbf.bugfinder.mutator.javaTransformations

import com.intellij.psi.JavaTokenType
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.util.NodeCollector
import com.stepanov.bbf.bugfinder.util.debugPrint
import com.stepanov.bbf.bugfinder.util.getAllChildrenNodes
import com.stepanov.bbf.reduktor.parser.PSICreator
import java.io.File
import kotlin.random.Random

class ChangeRandomJavaASTNodesFromAnotherTrees : Transformation() {

    override fun transform() {
        val randConst = Random.nextInt(numOfTries.first, numOfTries.second)
        val nodes = file.node.getAllChildrenNodes()
        log.debug("Trying to change some java nodes to nodes from other programs $randConst times")
        for (i in 0..randConst) {
            log.debug("Try №$i of $randConst")
            val randomNode = nodes[Random.nextInt(0, nodes.size - 1)]
            if (randomNode.elementType in excludes) continue
            //Searching nodes of same type in another files
            val line = File("databaseJava.txt").bufferedReader().lines()
                .filter { it.takeWhile { it != ' ' } == randomNode.elementType.toString() }.findFirst()
            if (!line.isPresent) continue
            val files = line.get().dropLast(1).takeLastWhile { it != '[' }.split(", ")
            val randomFile =
                if (files.size == 1)
                    files[0]
                else
                    files[Random.nextInt(0, files.size - 1)]
            val psi = PSICreator
                .getPsiForJava(File("${CompilerArgs.javaBaseDir}/$randomFile").readText(), file.project)
            val sameTypeNodes = psi.node.getAllChildrenNodes().filter { it.elementType == randomNode.elementType }
            val targetNode =
                if (sameTypeNodes.size == 1)
                    sameTypeNodes[0]
                else
                    sameTypeNodes[Random.nextInt(0, sameTypeNodes.size - 1)]
            checker.replaceNodeIfPossible(randomNode, targetNode)
        }
    }

    val numOfTries = 50 to 1000

    val excludes = listOf(
        JavaTokenType.TRUE_KEYWORD,
        JavaTokenType.FALSE_KEYWORD,
        JavaTokenType.NULL_KEYWORD,
        JavaTokenType.ABSTRACT_KEYWORD,
        JavaTokenType.ASSERT_KEYWORD,
        JavaTokenType.BOOLEAN_KEYWORD,
        JavaTokenType.BREAK_KEYWORD,
        JavaTokenType.BYTE_KEYWORD,
        JavaTokenType.CASE_KEYWORD,
        JavaTokenType.CATCH_KEYWORD,
        JavaTokenType.CHAR_KEYWORD,
        JavaTokenType.CLASS_KEYWORD,
        JavaTokenType.CONST_KEYWORD,
        JavaTokenType.CONTINUE_KEYWORD,
        JavaTokenType.DEFAULT_KEYWORD,
        JavaTokenType.DO_KEYWORD,
        JavaTokenType.DOUBLE_KEYWORD,
        JavaTokenType.ELSE_KEYWORD,
        JavaTokenType.ENUM_KEYWORD,
        JavaTokenType.EXTENDS_KEYWORD,
        JavaTokenType.FINAL_KEYWORD,
        JavaTokenType.FINALLY_KEYWORD,
        JavaTokenType.FLOAT_KEYWORD,
        JavaTokenType.FOR_KEYWORD,
        JavaTokenType.GOTO_KEYWORD,
        JavaTokenType.IF_KEYWORD,
        JavaTokenType.IMPLEMENTS_KEYWORD,
        JavaTokenType.IMPORT_KEYWORD,
        JavaTokenType.INSTANCEOF_KEYWORD,
        JavaTokenType.INT_KEYWORD,
        JavaTokenType.INTERFACE_KEYWORD,
        JavaTokenType.LONG_KEYWORD,
        JavaTokenType.NATIVE_KEYWORD,
        JavaTokenType.NEW_KEYWORD,
        JavaTokenType.PACKAGE_KEYWORD,
        JavaTokenType.PRIVATE_KEYWORD,
        JavaTokenType.PUBLIC_KEYWORD,
        JavaTokenType.SHORT_KEYWORD,
        JavaTokenType.SUPER_KEYWORD,
        JavaTokenType.SWITCH_KEYWORD,
        JavaTokenType.SYNCHRONIZED_KEYWORD,
        JavaTokenType.THIS_KEYWORD,
        JavaTokenType.THROW_KEYWORD,
        JavaTokenType.PROTECTED_KEYWORD,
        JavaTokenType.TRANSIENT_KEYWORD,
        JavaTokenType.RETURN_KEYWORD,
        JavaTokenType.VOID_KEYWORD,
        JavaTokenType.STATIC_KEYWORD,
        JavaTokenType.STRICTFP_KEYWORD,
        JavaTokenType.WHILE_KEYWORD,
        JavaTokenType.TRY_KEYWORD,
        JavaTokenType.VOLATILE_KEYWORD,
        JavaTokenType.THROWS_KEYWORD,
        JavaTokenType.OPEN_KEYWORD,
        JavaTokenType.MODULE_KEYWORD,
        JavaTokenType.REQUIRES_KEYWORD,
        JavaTokenType.EXPORTS_KEYWORD,
        JavaTokenType.OPENS_KEYWORD,
        JavaTokenType.USES_KEYWORD,
        JavaTokenType.PROVIDES_KEYWORD,
        JavaTokenType.TRANSITIVE_KEYWORD,
        JavaTokenType.TO_KEYWORD,
        JavaTokenType.WITH_KEYWORD,
        JavaTokenType.VAR_KEYWORD,
        JavaTokenType.YIELD_KEYWORD,
        JavaTokenType.RBRACE,
        JavaTokenType.LBRACE,
        JavaTokenType.WHITE_SPACE,
        JavaTokenType.RPARENTH,
        JavaTokenType.RBRACKET,
        JavaTokenType.LPARENTH,
        JavaTokenType.LBRACKET
    )
}