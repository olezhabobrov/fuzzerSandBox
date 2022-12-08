package com.stepanov.bbf.bugfinder.generator.constructor

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.types.KotlinType
import com.stepanov.bbf.bugfinder.util.flatMap

class ConstructionContext {

    fun getUsageOfType(type: KotlinType): UsingExample? {
        val usagesOfNeededType = classesAndUsages.values
            .filterNotNull()
            .flatMap { it.filter { it.type == type } }
        return if (usagesOfNeededType.isEmpty()) null else usagesOfNeededType.random()
    }

    var classesAndUsages: MutableMap<ASTNode, List<UsingExample>?> = mutableMapOf()
}

data class UsingExample(val node: ASTNode, val type: KotlinType) {

}