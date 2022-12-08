package com.stepanov.bbf.bugfinder.generator.targetsgenerators

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.*

object TypeParamsReplacer {

    fun throwTypeParams(
        fromType: KotlinType?,
        targetDescriptor: FunctionDescriptor
    ): Pair<FunctionDescriptor, Map<String, KotlinType?>>? {
        val retType = targetDescriptor.returnType!!
        val realTypeParams = fromType?.arguments?.map { it.type.upperIfFlexible() } ?: emptyList()
        val implSupertypeTypeParams = retType.arguments.map { it.type.name.toString() }
        val oldToRealTypeParams = mutableMapOf<String, KotlinType?>()
        for (tp in targetDescriptor.typeParameters) {
            val name = tp.name.asString()
            val index = implSupertypeTypeParams
                .indexOf(name)
                .let { if (it == -1) implSupertypeTypeParams.indexOf("$name?") else it }
            val upperBound = tp.upperBounds.firstOrNull()?.let { oldToRealTypeParams[it.name] ?: it }
            val newTypeParameter =
                if (realTypeParams.isNotEmpty() && index != -1 && !realTypeParams[index].isAnyOrNullableAny()) {
                    realTypeParams[index]
                } else {
                    RandomTypeGenerator.generateRandomTypeWithCtx(upperBound)
                }
            if (newTypeParameter != null && upperBound != null && !weakCheckTypeParamsBounds(newTypeParameter, upperBound)) {
                return null
            }
            oldToRealTypeParams[name] = newTypeParameter
        }
//        val typeParams = targetDescriptor.typeParameters.map {
//            val name = it.name.asString()
//            val index = implSupertypeTypeParams
//                .indexOf(name)
//                .let { if (it == -1) implSupertypeTypeParams.indexOf("$name?") else it }
//            if (index != -1) {
//                realTypeParams[index]
//            } else {
//                typeGenerator.generateRandomTypeWithCtx(it.upperBounds.firstOrNull())
//                //name
//            }
//        }
        //val oldToRealTypeParams = targetDescriptor.typeParameters.map { it.name.asString() }.zip(typeParams).toMap()
        val substitutedDescriptor = substituteTypeParams(targetDescriptor, oldToRealTypeParams) ?: return null
        return substitutedDescriptor to oldToRealTypeParams
    }

    fun throwTypeParams(
        fromType: KotlinType,
        targetDescriptor: ClassDescriptor,
        withoutParams: Boolean = false
    ): Pair<FunctionDescriptor, Map<String, KotlinType?>>? {
        val implConstr =
            if (CompilerArgs.isABICheckMode)
                targetDescriptor.constructors
                    .filter { it.visibility.isPublicAPI }
                    .minByOrNull { it.valueParameters.size }!!
            else
                targetDescriptor.constructors
                    .filter { it.visibility.isPublicAPI }
                    .filter { it.valueParameters.all { !it.type.hasTypeParam() } || !withoutParams }
                    .randomOrNull() ?: return null
        val implSupertype =
            targetDescriptor.getAllSuperClassifiers()
                .find { it.name.asString() == fromType.constructor.toString() } as? ClassDescriptor
        val realTypeParams = fromType.arguments.map {
            if (it is FlexibleType) {
                it.upperBound.asTypeProjection()
            } else {
                it.type.asTypeProjection()
            }
        }
        val implSupertypeTypeParams = implSupertype!!.declaredTypeParameters.map { it.name }
        val oldToRealTypeParams = mutableMapOf<String, KotlinType?>()
        for (tp in targetDescriptor.declaredTypeParameters) {
            val name = tp.name
            val index = implSupertypeTypeParams
                .indexOf(name)
                .let { if (it == -1) implSupertypeTypeParams.map { it.asString() }.indexOf("$name?") else it }
            val upperBound = tp.upperBounds.firstOrNull()?.let { oldToRealTypeParams[it.name] ?: it }
            val newTypeParameter =
                if (index != -1 && realTypeParams.size > index && !realTypeParams[index].type.isAnyOrNullableAny()) {
                    realTypeParams[index].type
                } else {
                    RandomTypeGenerator.generateRandomTypeWithCtx(upperBound)
                }
            if (newTypeParameter != null && upperBound != null && !weakCheckTypeParamsBounds(newTypeParameter, upperBound)) {
                return null
            }
            oldToRealTypeParams[name.asString()] = newTypeParameter
        }
        val substitutedDescriptor = substituteTypeParams(implConstr, oldToRealTypeParams) ?: return null
        return substitutedDescriptor to oldToRealTypeParams

//        val valueParams = produceValueParamsForConstructor(implConstr, oldToRealTypeParams)
//        val strTypeParams = implConstr.typeParameters.joinToString(prefix = " <", postfix = ">") { it.name.asString() }
//        val func = StringBuilder().apply {
//            append("fun")
//            if (strTypeParams != " <>") append(strTypeParams)
//            append(" ${targetDescriptor.name}")
//            append("($valueParams): ")
//            append(fromType.getNameWithoutError())
//            append(" = TODO()")
//        }.toString()
//        return Factory.psiFactory.createFunction(func) to oldToRealTypeParams
    }

    private fun substituteTypeParams(
        targetFunc: FunctionDescriptor,
        oldToRealTypeParams: Map<String, KotlinType?>
    ) = targetFunc.substitute(
        TypeSubstitutor.create(
            targetFunc.typeParameters
                .withIndex()
                .associateBy({ it.value.typeConstructor }) {
                    val newType =
                        oldToRealTypeParams[it.value.name.asString()] ?: RandomTypeGenerator.generateType("Any")!!
                    TypeProjectionImpl(newType)
                }
        )
    )

    private fun weakCheckTypeParamsBounds(type: KotlinType, upperBound: KotlinType): Boolean {
        val typeDescriptor = type.constructor.declarationDescriptor?.defaultType ?: return false
        val upperBoundDescriptor = upperBound.constructor.declarationDescriptor?.defaultType ?: return false
        if (typeDescriptor.name == "Any" && upperBoundDescriptor.name == "Any") return true
        if (typeDescriptor.name == upperBoundDescriptor.name) return true
        return typeDescriptor.supertypes().any { it.name == upperBoundDescriptor.name }
    }


//    private fun produceValueParamsForConstructor(
//        targetFunc: FunctionDescriptor,
//        oldToRealTypeParams: Map<String, KotlinType?>
//    ): String {
//        val targetFuncWithReplacedTP = targetFunc.substitute(
//            TypeSubstitutor.create(
//                targetFunc.typeParameters
//                    .withIndex()
//                    .associateBy({ it.value.typeConstructor }) {
//                        TypeProjectionImpl(oldToRealTypeParams[it.value.name.asString()]!!)
//                    }
//            )
//        ) ?: return ""
//        return targetFuncWithReplacedTP.valueParameters.joinToString { "${it.name}: ${it.type.getNameWithoutError()}" }
//    }
//
//    private fun makeNotNullable(type: String) = type.substringBeforeLast('?')

}