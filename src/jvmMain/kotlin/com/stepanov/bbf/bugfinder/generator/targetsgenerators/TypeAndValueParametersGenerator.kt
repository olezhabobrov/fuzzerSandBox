package com.stepanov.bbf.bugfinder.generator.targetsgenerators

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.util.sortedByTypeParams
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.replace
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import kotlin.system.exitProcess

open class TypeAndValueParametersGenerator(val file: KtFile) {

    val rtg = RandomTypeGenerator

    init {
        if (!rtg.isInitialized()) {
            val ctx = PSICreator.analyze(file) ?: exitProcess(0)
            rtg.setFileAndContext(file, ctx)
        }
    }


    fun generateTypeParameters(typeParameters: List<TypeParameterDescriptor>): List<KotlinType> {
        val typeParamToType = mutableMapOf<String, KotlinType>()
        val sortedTypeParams =
            typeParameters.sortedByTypeParams()
        for (tp in sortedTypeParams) {
            val upperBound = tp.upperBounds.firstOrNull()?.let {
                substituteTypeParams(typeParamToType, it)
            }
            val generatedTP = rtg.generateRandomTypeWithCtx(upperBound) ?: return listOf()
            typeParamToType["${tp.defaultType}"] = generatedTP
        }
        return typeParameters.map { typeParamToType["${it.name}"] ?: return listOf() }
    }

    private fun substituteTypeParams(subMap: Map<String, KotlinType>, type: KotlinType): KotlinType =
        subMap.getOrElse("$type") {
            val newArgs = type.arguments.map { arg ->
                subMap["${arg.type}"] ?: substituteTypeParams(subMap, arg.type)
            }.map { it.asTypeProjection() }
            type.replace(newArgs)
        }

    fun generateValueParameters(valueParameters: List<ValueParameterDescriptor>, depth: Int): List<String> {
        val rig = RandomInstancesGenerator(file, rtg.ctx)
        val res = mutableListOf<String>()
        for (param in valueParameters) {
            val instance = rig.generateValueOfType(param.type, depth + 1)
            res.add(instance)
        }
        return res.filter { it.isNotEmpty() }
    }

}