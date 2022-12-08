package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getTrue
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.resolve.calls.callUtil.getFunctionResolvedCallWithAssert
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import kotlin.random.Random

class ShuffleFunctionArguments : Transformation() {

    override fun transform() {
        val ctx = PSICreator.analyze(file)!!
        file.getAllPSIChildrenOfType<KtCallExpression>()
            .filter { Random.getTrue(10) }
            .forEach { call ->
                if (call.valueArgumentList == null || call.valueArguments.size < 2) return@forEach
                val callTarget = call.getFunctionResolvedCallWithAssert(ctx)
                val newValueArgs =
                    if (Random.getTrue(30))
                        blindShuffling(call, callTarget)
                    else
                        smartShuffling(call, callTarget)
                if (newValueArgs != null) checker.replaceNodeIfPossible(call.valueArgumentList!!, newValueArgs)
            }
    }

    private fun smartShuffling(
        call: KtCallExpression,
        callTarget: ResolvedCall<out FunctionDescriptor>
    ): KtValueArgumentList? {
        val valueParams =
            callTarget.resultingDescriptor.valueParameters.map { it.name.asString() to it.hasDefaultValue() }
        val callValueParams = call.valueArguments
        val callArgToParamName =
            callValueParams.mapIndexed { index, arg ->
                val argExpr = arg.getArgumentExpression()?.copy()
                arg.getArgumentName()?.let { argExpr to it.text }
                    ?: argExpr to valueParams[index].first
            }
        val shuffledArgs = callArgToParamName.shuffled().joinToString { "${it.second} = ${it.first?.text}" }
        return try {
            Factory.psiFactory.createCallArguments("(${shuffledArgs})")
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
    }

    private fun blindShuffling(
        call: KtCallExpression,
        callTarget: ResolvedCall<out FunctionDescriptor>
    ): KtValueArgumentList? {
        val resList = mutableListOf<String>()
        for (valueArg in call.valueArguments.shuffled()) {
            if (Random.getTrue(25) && valueArg.getArgumentExpression() != null) {
                if (Random.getTrue(50)) {
                    val randomName = callTarget.resultingDescriptor.valueParameters.randomOrNull()?.name?.asString()
                    if (randomName != null) {
                        resList.add("$randomName = ${valueArg.getArgumentExpression()!!.text}")
                    } else {
                        resList.add(valueArg.getArgumentExpression()!!.text)
                    }
                } else {
                    resList.add(valueArg.getArgumentExpression()!!.text)
                }
            } else {
                resList.add(valueArg.text)
            }
        }
        return try {
            Factory.psiFactory.createCallArguments("(${resList.joinToString()})")
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
    }

}