package com.stepanov.bbf.bugfinder.projectfuzzer

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.stepanov.bbf.bugfinder.executor.checkers.CompilationChecker
import com.stepanov.bbf.reduktor.util.replaceThis
import org.apache.log4j.Logger
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

class ClassSplitter(private val files: List<KtFile>, private val checker: CompilationChecker) {

    fun split(): List<KtFile> = TODO()
//    {
//        val res = files.toMutableList()
//        for (file in files) {
//            for (i in 0..randomConst) {
//                log.debug("i = $i")
//                val randomNode = file.getAllChildren().random()
//                if (randomNode.text.trim().isEmpty() || isInExcludes(randomNode)) continue
//                log.debug("Trying to move ${randomNode.text} to another file")
//                val newNode = when (randomNode) {
//                    is KtProperty -> randomNode.createExtensionFromProp()
//                    is KtNamedFunction -> randomNode.createExtensionFromFun()
//                    else -> randomNode
//                } ?: randomNode
//                val copy = newNode.copy()
//                val whitespace = replaceOnWhitespace(newNode)
//                //With imports
//                val newFile =
//                    factory.createFile(file.packageDirective?.text + file.importList?.text + "\n\n" + copy.text)
//                res.add(newFile)
////                checker.checkAndGetCompilerBugs(Project(res)).forEach { BugManager.saveBug(it) }
//                if (res.first().text.trim().isEmpty() || !checker.checkCompiling(Project(res))) {
//                    whitespace.replaceThis(copy)
//                    res.removeLast()
//                    log.debug("Not OK")
//                } else log.debug("OK")
//            }
//        }
//        return res
//    }

    private fun KtProperty.createExtensionFromProp(): PsiElement? {
        log.debug("Creating extension property")
        val kl = this.parents.find { p -> p is KtClass } ?: return null
        val klass = kl as KtClass
        return if (!this.isVar) {
            factory.createProperty("val ${klass.name}.${this.name} get() = ${this.initializer?.text}")
        } else {
            factory.createProperty("var ${klass.name}.${this.name} get() = ${this.initializer?.text} \n set(value) = TODO()")
        }
    }

    private fun KtNamedFunction.createExtensionFromFun(): PsiElement? {
        log.debug("Creating extension function")
        val kl = this.parents.find { p -> p is KtClass } ?: return null
        val klass = kl as KtClass
        val splitted = this.text.split("fun ")
        val newText = "${splitted[0]} fun ${klass.name}.${splitted.subList(1, splitted.size).joinToString()}"
        return factory.createFunction(newText)
    }

    private fun isInExcludes(el: PsiElement) =
        el is PsiComment ||
        el is KtImportDirective ||
        el is KtPackageDirective ||
        el is KtKeywordToken ||
        el is PsiWhiteSpace

    private fun replaceOnWhitespace(psiElement: PsiElement): PsiElement =
        factory.createWhiteSpace().let { psiElement.replaceThis(it); it }

    private fun <T> MutableList<T>.removeLast(): T =
        if (isEmpty()) throw NoSuchElementException("List is empty.") else removeAt(lastIndex)

    private val factory = KtPsiFactory(files.first().project)
    private val randomConst = 100
    private val log = Logger.getLogger("mutatorLogger")

}