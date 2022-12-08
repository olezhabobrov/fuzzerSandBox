package com.stepanov.bbf.bugfinder.executor

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.lang.StringBuilder

fun PsiFile.addMain(boxFuncs: List<KtNamedFunction>) {
    val m = StringBuilder()
    m.append("fun main(args: Array<String>) {\n")
    for (func in boxFuncs) m.append("println(${func.name}())\n")
    m.append("}")
    val mainFun = KtPsiFactory(this.project).createFunction(m.toString())
    this.add(KtPsiFactory(this.project).createWhiteSpace("\n\n"))
    this.add(mainFun)
}

fun PsiFile.addMainForPerformanceTesting(boxFuncs: List<KtNamedFunction>, times: Int) {
    val m = StringBuilder()
    m.append("fun main(args: Array<String>) {\n")
    for (func in boxFuncs) {
        m.append(
            """
        repeat($times) { ${func.name}() }
        """.trimIndent()
        )
        m.append("\n")
    }
    m.append("}")
    val mainFun = KtPsiFactory(this.project).createFunction(m.toString())
    this.add(KtPsiFactory(this.project).createWhiteSpace("\n\n"))
    this.add(mainFun)
}