package com.stepanov.bbf.bugfinder.generator.targetsgenerators

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.tryToCreateExpression
import com.stepanov.bbf.bugfinder.util.getTrue
import org.apache.log4j.Logger
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.types.KotlinType
import kotlin.random.Random

internal class FunInvocationGenerator(file: KtFile, val ctx: BindingContext) :
    TypeAndValueParametersGenerator(file) {

    private val MAX_DEPTH = 20
    private val log = Logger.getLogger("mutatorLogger")

    fun generateFunctionCallWithoutTypeParameters(
        functionDescriptor: FunctionDescriptor,
        typeParameters: Map<String, KotlinType?>,
        depth: Int
    ): PsiElement? {
        val randomInstanceGenerator = RandomInstancesGenerator(file, ctx)
        log.debug("Generating call of $functionDescriptor")
        val funcName =
            when (functionDescriptor) {
                is JavaClassConstructorDescriptor -> functionDescriptor.constructedClass.name
                is ClassConstructorDescriptor -> functionDescriptor.constructedClass.name
                else -> functionDescriptor.name
            }
        val typeParamsAsString =
            functionDescriptor.typeParameters.map {
                typeParameters[it.name.asString()] ?: "Any"
            }.let { if (it.isEmpty()) "" else "<${it.joinToString()}>" }
        val extRecValue =
            if (functionDescriptor.containingDeclaration is ClassDescriptor && functionDescriptor !is ConstructorDescriptor) {
                ((functionDescriptor.containingDeclaration) as ClassDescriptor).name.asString() + "."
            } else {
                functionDescriptor.extensionReceiverParameter?.type?.let {
                    randomInstanceGenerator.generateValueOfType(it, depth) + "."
                } ?: ""
            }
        val valueParams = functionDescriptor.valueParameters.map {
            if (it.isVararg) {
                val randomSize = Random.nextInt(1, 4)
                val elType = it.varargElementType!!
                List(randomSize) { "" }.map {
                    randomInstanceGenerator.generateValueOfType(elType, depth).let { it.ifEmpty { return null } }
                }.joinToString()
            } else {
                randomInstanceGenerator.generateValueOfType(it.type, depth).let { it.ifEmpty { return null } }
            }
        }.joinToString()
        val callExpression = """$extRecValue$funcName$typeParamsAsString($valueParams)"""
        return try {
            Factory.psiFactory.createExpression(callExpression)
        } catch (e: Exception) {
            null
        }
    }

    fun generateTopLevelFunInvocation(
        func: KtNamedFunction,
        ctx: BindingContext,
        depth: Int = 0
    ): KtCallExpression? {
        val descriptor = func.getDeclarationDescriptorIncludingConstructors(ctx) as? FunctionDescriptor ?: return null
        return generateTopLevelFunInvocation(descriptor, depth)
    }


    fun generateTopLevelFunInvocation(
        func: FunctionDescriptor,
        depth: Int = 0
    ): KtCallExpression? {
        val randomInstanceGenerator = RandomInstancesGenerator(file, ctx)
        val (descriptorWithoutTypeParams, realTypeParams) = TypeParamsReplacer.throwTypeParams(null, func)
            ?: return null
        val extensionReceiverType = descriptorWithoutTypeParams.extensionReceiverParameter?.value?.type
        val realTypeParamsAsString =
            if (func.typeParameters.isEmpty()) ""
            else func.typeParameters.map { realTypeParams[it.name.asString()] }
                .joinToString(prefix = "<", postfix = ">")
        val generatedExtensionReceiver =
            if (extensionReceiverType == null) {
                ""
            } else {
                randomInstanceGenerator.tryToGenerateValueOfType(extensionReceiverType, 2, depth + 1).ifEmpty { return null } + "."
            }
        val valueArgsAsString =
            if (descriptorWithoutTypeParams.valueParameters.isEmpty()) {
                "()"
            } else {
                val haveArgsWithDefaultValues = descriptorWithoutTypeParams.valueParameters.any { it.hasDefaultValue() }
                val generatedValueParams =
                    descriptorWithoutTypeParams.valueParameters
                        .map { valueParam ->
                            val valueParamType =
                                if (valueParam.isVararg) {
                                    valueParam.varargElementType ?: return null
                                } else {
                                    valueParam.type
                                }
                            if (valueParam.hasDefaultValue() && Random.getTrue(50)) {
                                ""
                            } else {
                                randomInstanceGenerator
                                    .tryToGenerateValueOfType(valueParamType, 2, depth + 1)
                                    .ifEmpty { return null }
                            }
                        }
                if (haveArgsWithDefaultValues) {
                    descriptorWithoutTypeParams.valueParameters
                        .zip(generatedValueParams)
                        .filter { it.second.isNotEmpty() }
                        .map { "${it.first.name.asString()} = ${it.second}" }
                        .shuffled()
                        .joinToString(prefix = "(", postfix = ")")
                } else {
                    generatedValueParams
                        .filter { it.isNotEmpty() }
                        .joinToString(prefix = "(", postfix = ")")
                }
            }
        val callExpressionAsString =
            "$generatedExtensionReceiver${func.name}$realTypeParamsAsString$valueArgsAsString"
        return Factory.psiFactory.tryToCreateExpression(callExpressionAsString) as? KtCallExpression
    }
}