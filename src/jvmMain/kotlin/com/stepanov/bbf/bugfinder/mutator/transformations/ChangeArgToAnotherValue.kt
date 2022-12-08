package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.FillerGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import kotlin.random.Random

class ChangeArgToAnotherValue : Transformation() {


    override fun transform() {
        val ctx = PSICreator.analyze(file, project) ?: return
        val randomInstancesGenerator = RandomInstancesGenerator(file as KtFile, ctx)
        for (func in file.getAllPSIChildrenOfType<KtNamedFunction>()) {
            val funcDescriptor =
                func.getDeclarationDescriptorIncludingConstructors(ctx) as? FunctionDescriptor ?: continue
            val callers = file.getAllPSIChildrenOfType<KtCallExpression>()
                .filter { it.getResolvedCall(ctx)?.resultingDescriptor?.findPsi() == func }
            for (call in callers.filter { Random.getTrue(30) }) {
                val valueArgs = call.getResolvedCall(ctx)?.valueArguments?.entries ?: continue
                for ((i, arg) in valueArgs.withIndex().filter { Random.getTrue(30) }) {
                    val argType = arg.key.type ?: continue
                    val argPSI =
                        (arg.value as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression() ?: continue
                    val replacement =
                        if (Random.getTrue(60)) {
                            getAvailablePropsAndExpressionsOfCompTypes(argPSI, argType, ctx)
                        } else {
                            null
                        } ?: randomInstancesGenerator.generateValueOfTypeAsExpression(argType)
                    replacement?.let { checker.replaceNodeIfPossible(argPSI, it) }
                }
            }
        }
    }


    private fun getAvailablePropsAndExpressionsOfCompTypes(
        node: PsiElement,
        type: KotlinType,
        ctx: BindingContext
    ): KtExpression? {
        val fillerGenerator = FillerGenerator(file as KtFile, ctx, mutableListOf())
        val potentialReplacement =
            (file as KtFile).getAvailableValuesToInsertIn(node, ctx)
                .filter { it.second != null }
                .map { it.first to it.second!! }
                .randomOrNull() ?: return null
        if (potentialReplacement.second == type) {
            return potentialReplacement.first
        }
        log.debug("Trying to get $type from ${potentialReplacement.second}")
        val callDescriptors = StdLibraryGenerator.generateCallSequenceToGetType(potentialReplacement.second, type)
        return callDescriptors
            .take(2)
            .mapNotNull { fillerGenerator.handleCallSeq(it) }
            .randomOrNull()
            ?.let {
                val isNullable = potentialReplacement.second.isNullable()
                val potRepText = potentialReplacement.first.text
                val prefix = if (isNullable) "($potRepText)?." else "(${potRepText})."
                try {
                    Factory.psiFactory.createExpressionIfPossible("$prefix${it.text}")
                } catch (e: Exception) {
                    null
                } catch (e: Error) {
                    null
                }
            }
    }
}