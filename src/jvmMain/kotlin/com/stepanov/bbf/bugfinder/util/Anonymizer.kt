package com.stepanov.bbf.bugfinder.util

import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.checkers.CompilationChecker
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import kotlin.random.Random

object Anonymizer {

    val counters = mutableListOf(0, 0, 0, 0, 0)
    fun anon(project: Project): Boolean {
        val psi = project.files.first().psiFile
        val checker = CompilationChecker(JVMCompiler())

        /*if (f.name != "singleAssignmentToVarargInAnnotation.kt") continue*/
        val named = mutableListOf<KtNamedDeclaration>()
        for (child in psi.getAllChildren()) {
            if (child is KtNamedDeclaration
                && child.name != null && !child.name!!.startsWith("<")
                && !child.text.contains(Regex("""fun box\d*\("""))
                && child !is KtParameter && child !is KtPrimaryConstructor && child !is KtSecondaryConstructor
                && child.modifierList?.let { !it.hasModifier(KtTokens.OVERRIDE_KEYWORD) } != false
                && child.modifierList?.let { !it.hasModifier(KtTokens.OPERATOR_KEYWORD) } != false
            ) {
                if (named.all { it.name != child.name })
                    named.add(child)
            }
        }
        //Rename box
        psi.getAllPSIChildrenOfType<KtNamedFunction>()
            .firstOrNull { it.text.contains(Regex("""fun box\d*\(""")) }
            ?.let { tryToReplace(psi, "box${Random.nextInt(100, 1000)}", it, checker, project) }
        named.forEach { tryToReplace(psi, generateNewName(it), it, checker, project) }
        return true
    }

    private fun tryToReplace(
        psi: PsiFile,
        newName: String,
        n: KtNamedDeclaration,
        checker: CompilationChecker,
        project: Project
    ): Boolean {
        val oldExpr = n.copy()
        val usages = psi.getAllChildren()
            .filter { it.node.elementType == KtTokens.IDENTIFIER }
            .filter { it.text == n.name && it != n.nameIdentifier }
            .filter { !it.parents.any { it is KtImportDirective } }
        //val usages = psi.getAllPSIChildrenOfType<KtNameReferenceExpression>().filter { it.text == n.name }
        val newNameRef = KtPsiFactory(psi.project).createSimpleName(newName)
        val newUsages = usages.map {
            val copy = newNameRef.copy()
            it.replaceThis(copy)
            it.copy() to copy
        }
        n.setName(newName)
        val res = checker.checkCompiling(project)
        if (!res) {
            newUsages.forEach { it.second.replaceThis(it.first) }
            n.replaceThis(oldExpr)
            if (!checker.checkCompiling(project)) {
                return false
            }
        }
        return true
    }

    private fun generateNewName(decl: KtNamedDeclaration): String =
        when (decl) {
            is KtClassOrObject -> "Kla${counters[0]++}"
            is KtFunction -> "fu${counters[1]++}"
            is KtProperty -> "prope${counters[2]++}"
            is KtParameter -> "para${counters[3]++}"
            else -> "Ty${counters[4]++}"
        }

    fun resetCounters() = counters.replaceAll { 0 }
}