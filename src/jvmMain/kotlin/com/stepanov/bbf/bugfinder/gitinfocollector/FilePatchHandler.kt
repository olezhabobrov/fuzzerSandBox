package com.stepanov.bbf.bugfinder.gitinfocollector

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.stepanov.bbf.bugfinder.util.getNodesBetweenWhitespaces
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parents
import kotlin.system.exitProcess

class FilePatchHandler(private val patches: List<FilePatch>) {

    fun getListOfAffectedFunctions(skipTests: Boolean): List<PsiElement> {
        val affectedFuncs = mutableSetOf<PsiElement>()
        val filteredPatches =
            if (skipTests) patches.filterNot { it.fileName.let { it.contains("testData") || it.contains("tests") } }
            else patches
        val patchToPsi = filteredPatches.map {
            when {
                it.fileName.endsWith(".kt") -> it to PSICreator.getPsiForTextWithName(
                    it.text,
                    it.fileName.substringAfterLast('/')
                )
                it.fileName.endsWith(".java") -> it to PSICreator.getPsiForJava(it.text)
                else -> it to null
            }
        }.filter { it.second != null }
        for ((filePatch, psiFile) in patchToPsi) {
            for (patch in filePatch.patches) {
                val affectedNodes =
                    psiFile!!.getNodesBetweenWhitespaces(patch.startAddLine, patch.startAddLine + patch.numOfAddedLines)
                affectedNodes.forEach { node ->
                    if (node is PsiMethod || node is KtNamedFunction) affectedFuncs.add(node)
                    node.parents.forEach { pnode ->
                        if (pnode is PsiMethod || pnode is KtNamedFunction) affectedFuncs.add(pnode)
                    }
                }
            }
        }
        return affectedFuncs.toList()
    }
}