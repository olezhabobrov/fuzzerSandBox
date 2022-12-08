package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.psi.KtTypeReference
import kotlin.random.Random
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory

class AddNullabilityTransformer : Transformation() {

    override fun transform() {
        file.getAllPSIChildrenOfType<KtTypeReference>()
            .asSequence()
            .filterNot { it.textContains('?') }
            .filter { Random.nextBoolean() }
            .forEach { addNullability(it) }
    }

    fun addNullability(ref: KtTypeReference) {
        val newRef = psiFactory.createTypeIfPossible("(${ref.typeElement?.text})?") ?: return
        checker.replaceNodeIfPossible(ref, newRef)
    }

}