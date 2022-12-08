package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiFile
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

object Factory {
    val file = PSICreator.getPSIForText("")
    val psiFactory = KtPsiFactory(file.project)

    fun KtPsiFactory.tryToCreateExpression(text: String) =
        try {
            psiFactory.createExpressionIfPossible(text)
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
}
//abstract class Factory {
//    companion object {
//
//        val psiFactory = KtPsiFactory(file.project)
//    }
//
//    val psiFactory = KtPsiFactory(file.project)
//}