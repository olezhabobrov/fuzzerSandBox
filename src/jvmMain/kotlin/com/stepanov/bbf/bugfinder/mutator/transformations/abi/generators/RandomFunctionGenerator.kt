package com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GFunction
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GStructure
import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.lexer.KtTokens
import com.stepanov.bbf.bugfinder.util.ModifierSets
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import kotlin.random.Random

class RandomFunctionGenerator(
    file: KtFile,
    ctx: BindingContext,
    val gClass: GClass? = null
) : DSGenerator(file, ctx) {

    private var gFunc = GFunction()
    private var parentMinVisibility = "public"

    private fun generateModifiers(): MutableList<String> =
        mutableListOf(
            listOf("inline", "", "", "", "").random(),
            (ModifierSets.FUNCTION_MODIFIER.types.toList().map { it.toString() }.filter { it != "operator" }
                    + listOf("", "", "")).random(),
            (ModifierSets.VISIBILITY_MODIFIER.types.toList().map { it.toString() }
                    + listOf("")).random()
        ).let { list ->
            if (gClass != null) {
                val inMod =
                    (ModifierSets.INHERITANCE_MODIFIER.types.toList()
                        .filter { it.toString() != KtTokens.SEALED_KEYWORD.toString() }
                        .map { it.toString() } + listOf("", "", "", "", "")).random()
                list.add(inMod)
                if (gClass.isObject() && list[2] == "protected") list[2] = ""
                if (gClass.isFunInterface()) list[3] = "abstract"
                if (!gClass.isInterface() && !gClass.isAbstract() && !gClass.isFunInterface() && inMod == "abstract") {
                    list[3] = ""
                }
                if (list[3] == "open" && list[2] == "private") list[3] = ""
                if (list[3] == "abstract") {
                    list[0] = ""
                    if (list[2] == "private" || list[2] == "internal") list[2] = ""
                }
                if (list[0] == "inline" && list[3] == "open") list[3] = "final"
                if (list[1] == "tailrec" && list[3] == "open") list[3] = "final"
            }
            if (gClass?.isInterface() == true || gClass?.isAbstract() == true) {
                if (list[3] == "final") list[3] = ""
                if (list[1].let { it == "external" || it == "tailrec" }) list[1] = ""
                if (list[1].trim().let { it != "infix" || it != "operator" }) list[1] = ""
                list[2] = if (Random.getTrue(80) || list[3] == "abstract") "" else "private"
                if (list[2] == "internal") list[2] = listOf("public", "private").random()
                if (list[0] == "inline") list[2] = "private"
            }
            if (list[1] == "external") list[0] = ""
            list
        }

    private fun generateExtension(typeArgs: List<String>): String {
        val prob = if (gFunc.modifiers.contains("infix") || gFunc.modifiers.contains("operator")) 100 else 10
        if (!Random.getTrue(prob)) return ""
        val t = randomTypeGenerator.generateRandomTypeWithCtx() ?: return ""
        val strT =
            if (t.arguments.isEmpty() || typeArgs.isEmpty()) {
                t.toString()
            } else {
                t.toString().substringBefore('<') + t.arguments.joinToString(prefix = "<", postfix = ">") {
                    if (Random.getTrue(20)) typeArgs.random() else it.toString()
                }
            }
        return strT
    }

    private fun generateArgs(typeArgs: List<String>): List<String> {
        var rightBound = if (gFunc.modifiers.contains("infix")) 2 else 5
        if (gFunc.modifiers.contains("operator")) rightBound = getNumOfArgsAndRTVForOperator(gFunc.name).first + 1
        val args =
            if (rightBound - 1 <= 0)
                mutableListOf()
            else
                MutableList(Random.nextInt(1, rightBound)) {
                    val t = randomTypeGenerator.generateRandomTypeWithCtx() ?: return listOf()
                    if ("$t".startsWith("Function") && gFunc.isInline() && Random.getTrue(50))
                        "crossinline ${'a' + it}: $t"
                    else "${'a' + it}: $t"
                }
        val argsWithTypeArgs =
            if (typeArgs.isEmpty())
                args
            else
                args.map {
                    if (Random.getTrue(30)) {
                        it.replaceAfter(':', " ${typeArgs.random()}")
                    } else it
                }
        return argsWithTypeArgs
    }

    private fun generateName(): String {
        if (gFunc.modifiers.contains("operator")) {
            return OperatorConventions.CONVENTION_NAMES.random().asString()
        }
        return Random.getRandomVariableName(5)
    }

    //TODO handle all possible operators
    private fun getNumOfArgsAndRTVForOperator(operator: String): Pair<Int, String> {
        return when (operator) {
            in OperatorConventions.UNARY_OPERATION_NAMES.values.map { it.asString() } -> 0 to "type"
            in OperatorConventions.BINARY_OPERATION_NAMES.values.map { it.asString() } -> 1 to "_"
            in OperatorConventions.ASSIGNMENT_OPERATIONS.values.map { it.asString() } -> 1 to "Unit"
            else -> 0 to "Boolean"
        }
    }

    private fun generateRtv(): String {
        if (gFunc.modifiers.contains("operator")) {
            when (getNumOfArgsAndRTVForOperator(gFunc.name).second) {
                "type" -> return gFunc.extensionReceiver
                "_" -> return randomTypeGenerator.generateRandomTypeWithCtx().toString()
                else -> return getNumOfArgsAndRTVForOperator(gFunc.name).second
            }
        }
        val randomType = randomTypeGenerator.generateRandomTypeWithCtx() ?: return ""
        return gClass?.let {
            if (Random.getTrue(30)) randomType.replaceTypeOrRandomSubtypeOnTypeParam(it.typeParams)
            else "$randomType"
        } ?: "$randomType"
    }

    private fun generateBody(): String =
        when {
            gFunc.modifiers.contains("external") || gFunc.isAbstract() -> ""
            gFunc.isPrivate() -> "= TODO()"
            gClass?.isInterface() == true -> if (Random.nextBoolean()) "" else "= TODO()"
            else -> "= TODO()"
        }

    override fun generateTypeParams(withModifiers: Boolean): List<String> {
        val typeParams = super.generateTypeParams(withModifiers)
        return typeParams.map {
            val prefix = if (gFunc.isInline() && Random.getTrue(50))
                "reified " else ""
            val postFix = it.substringAfter("in ").substringAfter("out ")
            "$prefix$postFix"
        }
    }


    override fun simpleGeneration(): PsiElement {
        with(gFunc) {
            modifiers = generateModifiers()
            val genTypeArgs = if (gClass?.isFunInterface() == true) listOf() else generateTypeParams(false)
            val genTypeArgsWObounds = genTypeArgs.map { it.substringBefore(':').substringAfter("reified ").trim() }
            typeArgs = genTypeArgs
            extensionReceiver = generateExtension(genTypeArgsWObounds)
            name = generateName()
            args = generateArgs((gClass?.typeParams ?: listOf()) + genTypeArgsWObounds)
            rtvType = generateRtv()
            body = generateBody()
        }
        return gFunc.toPsi()
    }

    override fun partialGeneration(initialStructure: GStructure): PsiElement? {
        gFunc = initialStructure as? GFunction ?: return null
        with(gFunc) {
            if (modifiers.isEmpty()) modifiers = generateModifiers()
            if (typeArgs.isEmpty()) {
                val genTypeArgs = if (gClass?.isFunInterface() == true) listOf() else generateTypeParams(false)
                typeArgs = genTypeArgs
            }
            val genTypeArgsWObounds = typeArgs.map { it.substringBefore(':').substringAfter("reified ").trim() }
            if (extensionReceiver.isEmpty()) extensionReceiver = generateExtension(genTypeArgsWObounds)
            if (name.isEmpty()) name = generateName()
            if (args.isEmpty()) args = generateArgs(genTypeArgsWObounds)
            if (rtvType.isEmpty()) rtvType = generateRtv()
            if (body.isEmpty()) body = generateBody()
        }
        return gFunc.toPsi()
    }

    override fun beforeGeneration() {
        parentMinVisibility = randomTypeGenerator.minVisibility
        randomTypeGenerator.minVisibility = "public"
    }

    override fun afterGeneration(psi: PsiElement) {
        if (gFunc.isAbstract() || gClass?.isInterface() == true) return
        val parentKlassVisibility = gClass?.getVisibility() ?: "public"
        if (compareDescriptorVisibilitiesAsStrings(parentKlassVisibility, randomTypeGenerator.minVisibility) >= 0) {
            randomTypeGenerator.minVisibility = parentMinVisibility
            return
        }
        //Visibility modifier
        val mod = generateVisibilityModifier(randomTypeGenerator.minVisibility)
        val ktModifier =
            when (mod) {
                "private" -> KtTokens.PRIVATE_KEYWORD
                "internal" -> KtTokens.INTERNAL_KEYWORD
                "public" -> KtTokens.PUBLIC_KEYWORD
                else -> KtTokens.PROTECTED_KEYWORD
            }
        val ktFun = psi as? KtNamedFunction ?: return
        ktFun.addModifier(ktModifier)
        if (ktFun.hasModifier(KtTokens.OPEN_KEYWORD) && ktFun.hasModifier(KtTokens.PRIVATE_KEYWORD))
            ktFun.removeModifier(KtTokens.OPEN_KEYWORD)
        val modList = ModifierSets.VISIBILITY_MODIFIER.types.toList().map { it.toString() }
        gFunc.modifiers.replaceAll { if (it in modList) mod else it }
        randomTypeGenerator.minVisibility = parentMinVisibility
    }
}