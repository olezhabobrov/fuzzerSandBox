package com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GStructure
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import kotlin.random.Random

//TODO make generation through GProperty
class RandomPropertyGenerator(
    private val file: KtFile,
    private val ctx: BindingContext,
    private val gClass: GClass = GClass()
) : DSGenerator(file, ctx) {

    var parentMinVisibility = "public"

    private fun generateInterestingProperty1(klasses: List<ClassDescriptor>): Pair<PsiElement, KotlinType?>? {
        val klass = klasses.find { it.name.asString() == gClass.name } ?: return null
        val memberToOverride =
            klass.defaultType.memberScope
                .getDescriptorsFiltered { true }
                .filterIsInstance<CallableMemberDescriptor>()
                .filter { it.kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE }
                .filter { it.name.asString().let { it != "equals" && it != "hashCode" && it != "toString" } }
                .randomOrNull() ?: return null
        val returnType = memberToOverride.returnType
        return try {
            when (memberToOverride) {
                is PropertyDescriptor -> {
                    var valOrVar = if (memberToOverride.isVar) "var" else "val"
                    if (valOrVar == "val" && Random.getTrue(15)) valOrVar = "var"
                    val def =
                        valOrVar + memberToOverride.toString().substringAfter(valOrVar).substringBefore(" defined")
                    Factory.psiFactory.createProperty(def) to returnType
                }
                is FunctionDescriptor -> {
                    val def = memberToOverride.toString().substringAfter("fun").substringBefore("defined ")
                    Factory.psiFactory.createFunction(def) to returnType
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    //Generate modifiers
    fun generateDelegate(): Pair<PsiElement, KotlinType?>? {
        if (gClass.isInline()) return null
        val delegates =
            StdLibraryGenerator.klasses.find { it.name.asString() == "Delegates" }
                ?.defaultType?.memberScope?.getDescriptorsFiltered { true }
                ?.filterIsInstance<FunctionDescriptor>()
                ?.filter { it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE }
                ?.let { if (it.isEmpty()) return null else it }
                ?: return null
        val f = delegates.random()
        val generated = randomInstancesGenerator.generateFunctionCall(f) as? KtCallExpression ?: return null
        val propType = generated.typeArguments.firstOrNull() ?: return null
        val varOrVal = if (Random.getTrue(50)) "val " else "var"
        val name = Random.getRandomVariableName(4)
        val resProp =
            Factory.psiFactory.createProperty("$varOrVal $name : ${propType.text} by Delegates.${generated.text}")
        return resProp to null
    }

    //RTV: psi to prop type
    fun generateInterestingProperty(klass: KtClassOrObject): Pair<PsiElement, KotlinType?>? {
        if (Random.getTrue(20)) return generateDelegate()
        //Get classes which are supertypes of us
//        val superClasses = file.getAllPSIChildrenOfType<KtTypeReference>()
//            .mapNotNull { it.getAbbreviatedTypeOrType(ctx)?.constructor?.declarationDescriptor?.findPackage() }
//            .firstOrNull { it.fqName == file.packageFqName }
//            ?.getMemberScope()?.getDescriptorsFiltered { true }
//            ?.filterIsInstance<ClassDescriptor>() ?: return null
        val classDescriptor = klass.getDeclarationDescriptorIncludingConstructors(ctx) as? ClassDescriptor ?: return null
        val superClasses = classDescriptor.getAllSuperClassifiersWithoutAnyAndItself().toList().mapNotNull { it as? ClassDescriptor }
        val children =
            superClasses.filter {
                it.name.asString() != gClass.name &&
                        it.getAllSuperClassifiers().any { it.name.asString() == gClass.name }
            }
        if (Random.getTrue(30) || children.isEmpty()) return generateInterestingProperty1(superClasses)
        val child = children.randomOrNull() ?: return null
        val overrides =
            child.defaultType.memberScope.getDescriptorsFiltered { true }
                .filterIsInstance<CallableMemberDescriptor>()
                .filter { DescriptorUtils.isOverride(it) }
                .filter { it.name.asString().let { it != "equals" && it != "hashCode" && it != "toString" } }
        val randomOverride = overrides.randomOrNull() ?: return null
        val returnType = randomOverride.returnType
        var isFun = true
        val overrideToStr =
            if (randomOverride is SimpleFunctionDescriptor) {
                if (gClass.isInterface() || gClass.isAbstract() && Random.nextBoolean())
                    "public abstract fun ${
                        randomOverride.toString().substringAfter("fun").substringBefore("defined ")
                    }"
                else
                    "public open fun ${
                        randomOverride.toString().substringAfter("fun").substringBefore("defined ")
                    } = TODO()"
            } else if (randomOverride is PropertyDescriptor) {
                isFun = false
                val varOrVal = if (randomOverride.isVar) "var" else "val"
                if (gClass.isInterface() || gClass.isAbstract() && Random.nextBoolean())
                    "public abstract $varOrVal ${
                        randomOverride.toString().substringAfter(varOrVal).substringBefore("defined ")
                    }"
                else "public open $varOrVal ${
                    randomOverride.toString().substringAfter(varOrVal).substringBefore("defined ")
                } = TODO()"
            } else ""
        return try {
            if (isFun) Factory.psiFactory.createFunction(overrideToStr) to returnType
            else Factory.psiFactory.createProperty(overrideToStr) to returnType
        } catch (e: Exception) {
            null
        }
    }

    private fun makePropExtension(prop: String, isVar: Boolean): String {
        val rec = randomTypeGenerator.generateRandomTypeWithCtx()
        val body =
            if (!prop.contains('=')) "TODO()"
            else prop.substringAfter('=').let { if (it.trim().isEmpty()) "TODO()" else it }
        val getter = "get() = $body"
        val valOrVar = if (isVar) "var" else "val"
        return "$valOrVar $rec.${prop.substringBefore('=')}\n$getter\n"
    }

    fun generateRandomProperty(fromObject: Boolean): String {
        val randomType = randomTypeGenerator.generateRandomTypeWithCtx() ?: return ""
        val withTypeParams = randomType.replaceTypeOrRandomSubtypeOnTypeParam(gClass.typeParams)
        if (Random.getTrue(20)
            && !randomType.isPrimitiveTypeOrNullablePrimitiveTypeOrString()
            && !randomType.isNullable()
            && !gClass.isInterface()
            && !gClass.isInline()
        ) return "lateinit var ${Random.getRandomVariableName(4)}: $withTypeParams"
        var modifiers =
            if (fromObject) listOf("internal", "private", "public").random()
            else listOf("internal", "private", "public", "protected").random()
        modifiers += " "
        if (gClass.isInterface()) modifiers = ""
        if (gClass.isObject() && modifiers.contains("protected")) modifiers = ""
        modifiers += if (gClass.isAbstract() && !fromObject && Random.getTrue(60)) "abstract " else ""
        if (modifiers.contains("abstract")) modifiers = "abstract "
        modifiers += if (Random.nextBoolean()) "val" else "var"
        val isVar = modifiers.contains("var")
        if (modifiers.contains("abstract")) return "$modifiers ${Random.getRandomVariableName(4)}: $withTypeParams"
        val defaultValue =
            if (gClass.isInterface()) ""
            else if (gClass.isInline() || Random.nextBoolean() || withTypeParams != randomType.toString()) "= TODO()"
            else {
                val v = randomInstancesGenerator.generateValueOfType(randomType.makeNotNullable())
                if (v.isEmpty()) "= TODO()" else "= $v"
            }
        val definition = "${Random.getRandomVariableName(4)}: $withTypeParams "
        val p = "$definition $defaultValue"
        if (Random.getTrue(70) || gClass.isInline()) {
            val getter =
                if (gClass.isInterface()) ""
                else "get() $defaultValue"
            val setter =
                if (isVar && !gClass.isInterface()) {
                    if (fromObject || defaultValue.contains("TODO()") || Random.getTrue(30) || gClass.isInline()) {
                        "set(value) = TODO()\n"
                    } else {
                        "private set\n"
                    }
                } else ""
            val d =
                if (setter.contains("private set")) {
                    defaultValue
                } else ""
            return "$modifiers $definition $d\n$getter\n$setter"
        }
        return if (Random.getTrue(30) && !isVar) makePropExtension(p, modifiers.contains("var"))
        else "$modifiers $p"
    }


    override fun simpleGeneration(): PsiElement? =
        try {
            Factory.psiFactory.createProperty(generateRandomProperty(false))
        } catch (e: Exception) {
            null
        }

    override fun partialGeneration(initialStructure: GStructure): PsiElement? = simpleGeneration()

    override fun beforeGeneration() {
        parentMinVisibility = randomTypeGenerator.minVisibility
        randomTypeGenerator.minVisibility = "public"
    }

    override fun afterGeneration(psi: PsiElement) {
        val ktProp = psi as? KtProperty ?: return
        val parentKlassVisibility = gClass.getVisibility()
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
        ktProp.addModifier(ktModifier)
        randomTypeGenerator.minVisibility = parentMinVisibility
    }

}