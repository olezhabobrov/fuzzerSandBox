package com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGeneratorForAnClass
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.typeUtil.isInterface
import com.stepanov.bbf.bugfinder.util.ModifierSets
import kotlin.random.Random

open class RandomClassGenerator(
    override val file: KtFile,
    override val ctx: BindingContext,
    override val depth: Int = 0,
    private val parentClass: GClass? = null
) : AbstractClassGenerator(file, ctx, depth) {

    private val inheritanceGenProb = 50

    override val classWord: String
        get() = "class"

    override fun generateModifiers(): MutableList<String> {
        val classModifiers =
            (ModifierSets.CLASS_MODIFIER.types.toList()
                .map { it.toString() } + listOf("inline"))
                .filter { it != "fun" && it != "companion" }
                .let {
                    if (depth == 0) it.filter { it != "inner" } else it
                }
        val classModifier = if (Random.getTrue(70)) {
            if (depth != 0 && Random.getTrue(50) && parentClass?.let { !it.isObject() } == true) "inner"
            else ""
        } else classModifiers.random()
        val visibilityModifiers =
            ModifierSets.VISIBILITY_MODIFIER.types.toList().map { it.toString() }.filter { it != "protected" }
        val visibilityModifier = if (Random.getTrue(20)) "" else visibilityModifiers.random()
        val inheritanceModifiers = ModifierSets.INHERITANCE_MODIFIER.types.toList().map { it.toString() }
        val inheritanceModifier =
            if (classModifier == "inline") "final"
            else if (Random.getTrue(20) || classModifier.isNotEmpty())
                ""
            else
                inheritanceModifiers.random().let { if (it == "inner" && classModifier.isNotEmpty()) "" else it }
        return mutableListOf(classModifier, visibilityModifier, inheritanceModifier)
    }

    private fun generateModifierForConstructorProperty(haveVararg: Boolean): String =
        if (gClass.isData()) if (Random.nextBoolean()) "var " else "val "
        else when (Random.nextInt(0, 3)) {
            1 -> "var "
            2 -> if (Random.nextBoolean() && !haveVararg) "vararg " else ""
            else -> "val "
        }

    override fun generateConstructor(): List<String> {
        var lb = 0
        val rb = 5
        var haveVararg = false
        if (gClass.isData()) lb = 1
        var numOfArgs = (lb..rb).random()
        if (numOfArgs != 0 && !gClass.isAnnotation() && Random.getTrue(15) && !gClass.isInline())
            gClass.constructorWord = "private constructor"
        if (gClass.isInline()) numOfArgs = 1
        if (gClass.isAnnotation()) {
            lb = 1
            numOfArgs = (lb..rb).random()
            return MutableList(numOfArgs) {
                "val ${Random.getRandomVariableName(3)}: ${RandomTypeGeneratorForAnClass.generateTypeForAnnotationClass()}"
            }
        }
        val args =
            if (numOfArgs == 0)
                mutableListOf()
            else
                MutableList(numOfArgs) {
                    var m = generateModifierForConstructorProperty(haveVararg)
                    if (m.trim() == "vararg") haveVararg = true
                    if (gClass.isInline()) m = "val "
                    "$m${Random.getRandomVariableName(3)}: " to randomTypeGenerator.generateRandomTypeWithCtx()
                }
        val typeArgs = gClass.typeParams.map { it.substringBefore(':') }
        if (args.any { it.second == null }) return emptyList()
        if (typeArgs.isEmpty() || gClass.isInline())
            return args.map {
                val defaultValue =
                    if (Random.getTrue(30) && !it.first.contains("vararg")) {
                        randomInstancesGenerator.generateValueOfType(it.second!!).let {
                            if (it.trim().isNotEmpty()) " = $it"
                            else ""
                        }
                    } else ""
                "${it.first}${it.second.toString()}$defaultValue"
            }
        return args.map { arg ->
            val type = arg.second!!
            val finalType = type.replaceTypeOrRandomSubtypeOnTypeParam(typeArgs)
            val defaultValue =
                if (finalType == arg.second.toString() && Random.getTrue(30)
                    && arg.second != null && !type.isInterface() && !type.isAbstractClass()
                    && !arg.first.contains("vararg")
                ) {
                    val genValue = randomInstancesGenerator.generateValueOfType(arg.second!!)
                    if (genValue.trim().isNotEmpty()) " = $genValue" else ""
                } else ""
            "${arg.first}$finalType$defaultValue"
        }
    }

    private fun generateSupertypes1(): Pair<String, ClassDescriptor>? {
        val openClass = randomTypeGenerator.generateOpenClassType() ?: return null
        var typeParams = openClass.declaredTypeParameters.map { typeParam ->
            val upperBounds = typeParam.upperBounds.let { if (it.isEmpty()) null else it.first() }
            randomTypeGenerator.generateRandomTypeWithCtx(upperBounds).toString()
        }
        val valueParams =
            when {
                openClass.constructors.any { it.valueParameters.isNotEmpty() && it.visibility.isPublicAPI } -> {
                    val generated = randomInstancesGenerator.generateValueOfType(openClass.defaultType, onlyImpl = true)
                    if (generated.isEmpty()) {
                        println("CANT GENERATE EXPRESSION FROM ${openClass.defaultType}")
                        "()"
                    } else {
                        val call = Factory.psiFactory.createExpression(generated) as KtCallExpression
                        typeParams = call.typeArguments.map { it.text }
                        val strValueParams = generated.substringAfter('(').substringBeforeLast(')')
                        "(${strValueParams})"
                    }
                }
                openClass.constructors.firstOrNull()?.valueParameters?.isEmpty() == true -> "()"
                else -> ""
            }
        val strTP = if (typeParams.isEmpty()) "" else typeParams.joinToString(prefix = " <", postfix = ">")
        val delegate =
            gClass.constructorArgs
                .filter { !it.startsWith("vararg") }
                .map {
                    val n = it.substringAfter("val").substringAfter("var").substringBefore(':').trim()
                    val t = it.substringAfter(":").substringBefore('=').trim()
                    n to t
                }
                .find { it.second == openClass.name.asString() }
        return if (openClass.kind == ClassKind.INTERFACE && delegate != null) "${openClass.name} by ${delegate.first}" to openClass
        else "${openClass.name}$strTP$valueParams" to openClass
    }

    override fun generateSupertypes(): List<String> {
        if (gClass.isInline()) return listOf()
        var hasClassAsSuperType = false
        val specifiers =
            if (gClass.isEnum() || gClass.isAnnotation())
                listOf()
            else if (Random.getTrue(100 - inheritanceGenProb))
                listOf()
            else
                List(Random.nextInt(1, 4)) {
                    val gType = generateSupertypes1()
                    if (gType != null) {
                        val isInterface = gType.second.defaultType.isInterface()
                        if (isInterface) gType.first
                        else if (!hasClassAsSuperType) {
                            hasClassAsSuperType = true
                            gType.first
                        } else null
                    } else null
                }.filterNotNull().filterDuplicatesBy { it.substringBefore('<') }
        return specifiers
    }

    override fun generateAnnotations(): List<String> =
        if (gClass.isAnnotation()) listOf("@Retention(AnnotationRetention.RUNTIME)")
        else listOf()

    override fun generateTypeParams(withModifiers: Boolean): List<String> {
        if (gClass.isEnum() || gClass.isAnnotation()) return listOf()
        return super.generateTypeParams(true)
    }

}
