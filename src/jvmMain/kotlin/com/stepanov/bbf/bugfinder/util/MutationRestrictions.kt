package com.stepanov.bbf.bugfinder.util

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

fun noBoxFunModifying(file: PsiFile): Boolean {
    val boxFun =
        file.getAllPSIChildrenOfType<KtNamedFunction>().firstOrNull { it.name?.contains("box") ?: false} ?: return false
    //Check if top level
    return boxFun.isTopLevel
}

fun noLastLambdaInFinallyBlock(file: PsiFile): Boolean {
    val finallyExpressions = file.getAllPSIChildrenOfType<KtFinallySection>().map { it.finalExpression }
    if (finallyExpressions.isEmpty()) return true
    return finallyExpressions.all { it.statements.lastOrNull() !is KtLambdaExpression }
}