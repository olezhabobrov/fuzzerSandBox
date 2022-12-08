package com.stepanov.bbf.bugfinder.mutator.transformations

import org.jetbrains.kotlin.psi.KtExpression

import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getRandomBoolean
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory

class ChangeVarToNull : Transformation() {

    override fun transform() {
        file.getAllPSIChildrenOfType<KtExpression>()
                .filter { getRandomBoolean(16) }
                .forEach { checker.replaceNodeIfPossible(it, psiFactory.createExpression("null")) }
    }

}