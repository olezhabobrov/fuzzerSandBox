package com.stepanov.bbf.bugfinder.generator

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.generator.subjectgenerator.Expression
import com.stepanov.bbf.bugfinder.util.debugPrint
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

class StructureGenerator {

    fun generate(): KtFile {
        File(CompilerArgs.pathToTmpFile).writeText("")
        val resultFile = PSICreator.getPSIForFile(CompilerArgs.pathToTmpFile)
        Expression.factory = KtPsiFactory(resultFile.project)
        resultFile.debugPrint()
        System.exit(0)
        return resultFile
    }
}