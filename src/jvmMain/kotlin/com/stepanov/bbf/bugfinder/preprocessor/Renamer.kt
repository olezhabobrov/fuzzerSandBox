package com.stepanov.bbf.bugfinder.preprocessor

import com.intellij.psi.PsiNamedElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfTwoTypes
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.replaceThis
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty

class Renamer {
    fun rename(files: List<KtFile>, forRenaming: Set<String>) {
        for (name in forRenaming) {
            //Searching for files for renaming
            val objectsForRenaming =
                files
                    .asSequence()
                    .map { it to it.getAllPSIChildrenOfTwoTypes<KtClassOrObject, KtProperty>() }
                    .map { it.first to it.second.find { (it as PsiNamedElement).name == name } }
                    .filter { it.second != null }
                    .map { it.first to (it.second as PsiNamedElement) }
                    .toList()
            //Rename
            for (i in 1 until objectsForRenaming.size) {
                val (file, el) = objectsForRenaming[i]
                val usages = file.getAllPSIChildrenOfType<KtNameReferenceExpression> { it.text == el.name }
                el.setName("Renamed$i${el.name}")
                val newNameReferenceExpr = Factory.psiFactory.createSimpleName(el.name!!)
                usages.forEach { it.replaceThis(newNameReferenceExpr.copy()) }
            }
        }
    }

}