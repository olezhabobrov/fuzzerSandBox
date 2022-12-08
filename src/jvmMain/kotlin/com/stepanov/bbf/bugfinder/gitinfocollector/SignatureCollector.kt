package com.stepanov.bbf.bugfinder.gitinfocollector

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import coverage.CoverageEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

object SignatureCollector {
    fun collectSignatures(funcs: List<PsiElement>): List<CoverageEntry> =
        if (funcs.isEmpty()) listOf()
        else
            KotlinSignatureCollector().collect(funcs.filterIsInstance<KtNamedFunction>()) +
            JavaSignatureCollector().collect(funcs.filterIsInstance<PsiMethod>())
}