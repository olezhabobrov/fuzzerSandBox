package com.stepanov.bbf.bugfinder.mutator.transformations


import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory
import com.stepanov.bbf.bugfinder.util.filterDuplicatesBy
import com.stepanov.bbf.bugfinder.util.getNameWithoutError
import com.stepanov.bbf.bugfinder.util.getTrue
import com.stepanov.bbf.bugfinder.util.supertypesWithoutAny
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import kotlin.random.Random

class ChangeTypes : Transformation() {

    override fun transform() {
        val typeReferences = file.getAllPSIChildrenOfType<KtTypeReference>()
            .filterDuplicatesBy { it.text }
            .map { it to it.getAbbreviatedTypeOrType(ctx!!) }
            .filter { it.second != null && Random.getTrue(20) }
        for ((typeRef, type) in typeReferences) {
            val replacement =
                type!!.supertypesWithoutAny()
                    .randomOrNull()
                    ?.let { psiFactory.createTypeIfPossible(it.getNameWithoutError()) }
                    ?: continue
            checker.replaceNodeIfPossible(typeRef, replacement)
        }
    }
}