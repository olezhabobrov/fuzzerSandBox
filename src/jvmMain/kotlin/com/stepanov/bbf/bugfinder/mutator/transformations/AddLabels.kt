package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import kotlin.random.Random

class AddLabels : Transformation() {
    override fun transform() {
        val randomLoops =
            file.getAllPSIChildrenOfFourTypes<KtForExpression, KtWhileExpression, KtDoWhileExpression, KtLambdaExpression>()
                .filter { Random.getTrue(25) && it.parent !is KtLabeledExpression }
                .map { it as KtExpression }
                .reversed()
        for (randomLoop in randomLoops) {
            val labelRandomName = Random.getRandomVariableName(1).toLowerCaseAsciiOnly()
            val newLabeledExpression = Factory.psiFactory.createLabeledExpression("$labelRandomName@${randomLoop.text}")
            checker.replaceNodeIfPossible(randomLoop, newLabeledExpression)
        }
    }
}