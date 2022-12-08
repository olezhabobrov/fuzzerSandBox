package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.stepanov.bbf.bugfinder.mutator.transformations.util.ScopeCalculator
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import kotlin.random.Random
import kotlin.system.exitProcess

class AddExpressionToLoop : Transformation() {
    override fun transform() {
        val ctx = PSICreator.analyze(file) ?: return
        val expressions = file.getAllPSIChildrenOfType<KtExpression>().filter { it.getType(ctx) != null }
        file.getAllPSIChildrenOfType<KtLoopExpression>()
            .reversed()
            .filter { Random.getTrue(20) }
            .filter { it.body is KtBlockExpression }
            .forEach { loop ->
                repeat(5) {
                    tryToAddExpressions(loop, expressions)
                }
            }
    }

    private fun tryToAddExpressions(loopExp: KtLoopExpression, expressionsToInsert: List<KtExpression>) {
        val blockBody = loopExp.body!! as KtBlockExpression
        val blockCopy = blockBody.copy()
        val blockTextLines = (blockBody.copy() as KtBlockExpression).let { blockCopy ->
            blockCopy.rBrace?.delete()
            blockBody.lBrace?.delete()
            blockCopy.text.split("\n").filter { it.trim().isNotEmpty() }
        }
        val randomLineNumber = Random.nextInt(0, blockTextLines.size)
        val randomExpressionToInsert = expressionsToInsert.randomOrNull()?.text ?: return
        val left = blockTextLines.take(randomLineNumber).joinToString("\n")
        val right = blockTextLines.drop(randomLineNumber).joinToString("\n")
        val newText = left + "\n${randomExpressionToInsert.trimEnd()}\n" + right
        try {
            val newBlock = Factory.psiFactory.createBlock(newText)
            blockBody.replaceThis(newBlock)
            if (!checker.checkCompiling()) {
                newBlock.replaceThis(blockCopy)
            }
        } catch (e: Exception) {
            return
        } catch (e: Error) {
            return
        }
        return
    }
}