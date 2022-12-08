package com.stepanov.bbf.bugfinder.generator.targetsgenerators

import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.util.FileMember
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import kotlin.system.exitProcess

object RandomExpressionGenerator {

    //TODO!! getLibraryCallsForType
    fun generate(availableMembers: List<FileMember>, neededType: KotlinType): KtExpression? {
        return null
    }
}