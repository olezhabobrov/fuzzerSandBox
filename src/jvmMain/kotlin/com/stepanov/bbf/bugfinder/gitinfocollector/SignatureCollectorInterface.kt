package com.stepanov.bbf.bugfinder.gitinfocollector

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import coverage.CoverageEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

internal interface SignatureCollectorInterface<T> {
    fun collect(funcs: List<T>): List<CoverageEntry>
}


