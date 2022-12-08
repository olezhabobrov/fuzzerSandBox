package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.util.filterDuplicatesBy
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getTrue
import com.stepanov.bbf.bugfinder.util.replaceThis
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import kotlin.random.Random
import kotlin.system.exitProcess

class AddCallableReference : Transformation() {
    override fun transform() {
        var ctx = PSICreator.analyze(file) ?: return
        val callsWithFunTypes =
            file.getAllPSIChildrenOfType<KtCallExpression>()
                .filter { it.valueArguments.any { it.getArgumentExpression()?.getType(ctx)?.isFunctionType == true } }
        val potentialCallableReferences =
            checker.project.files.flatMap {
                it.psiFile.getAllPSIChildrenOfType<KtReferenceExpression>()
                    .filter { it !is KtCallExpression }
                    .filterDuplicatesBy { it.text }
            }
        tryToReplaceCallableReferences(potentialCallableReferences)
        //Deal with single or not single lamda arg
        for (c in callsWithFunTypes) {
            val lambdaArgument = c.lambdaArguments.firstOrNull() ?: continue
            if (c.valueArgumentList == null) {
                val newValueArgumentList = Factory.psiFactory.createCallArguments("(${lambdaArgument.text})")
                lambdaArgument.replaceThis(newValueArgumentList)
            } else {
                val oldValueArgs = c.valueArguments.joinToString(", ") { it.text }
                val newValueArgumentList = Factory.psiFactory.createCallArguments("($oldValueArgs)")
                c.valueArgumentList!!.replaceThis(newValueArgumentList)
                lambdaArgument.delete()
            }
        }
        ctx = PSICreator.analyze(file) ?: return
        for (c in callsWithFunTypes) {
            if (Random.getTrue(50)) continue
            c.valueArguments.forEach { arg ->
                val argType = arg.getArgumentExpression()?.getType(ctx) ?: return@forEach
                if (!argType.isFunctionType) return@forEach
                for (ref in potentialCallableReferences.shuffled()) {
                    val newCallable = Factory.psiFactory.createCallableReferenceExpression("::${ref.text}")
                    print("TRYING TO REPLACE ${arg.text} on ${newCallable?.text} ")
                    if (newCallable != null) {
                        val res = checker.replaceNodeIfPossible(arg, newCallable)
                        println(res)
                    }
                }
            }
        }
    }

    private fun tryToReplaceCallableReferences(potentialCallableReferences: List<KtReferenceExpression>) {
        val callableReferences = file.getAllPSIChildrenOfType<KtCallableReferenceExpression>()
        val potentialCallableReferencesAsCallableReferences =
            potentialCallableReferences.mapNotNull { Factory.psiFactory.createCallableReferenceExpression("::${it.text}") }
        for (c in callableReferences) {
            for (potCallableReference in potentialCallableReferencesAsCallableReferences) {
                if (c.text == potCallableReference.text) continue
                val copy = potCallableReference.copy()
                c.replaceThis(copy)
                if (!checker.checkCompiling()) {
                    copy.replaceThis(c)
                } else break
            }
        }
    }
}