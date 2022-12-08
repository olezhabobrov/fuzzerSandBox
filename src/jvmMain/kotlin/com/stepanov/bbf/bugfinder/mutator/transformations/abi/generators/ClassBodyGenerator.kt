package com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GStructure
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType
import kotlin.collections.flatMap
import kotlin.random.Random

internal open class ClassBodyGenerator(
    private val file: KtFile,
    private val ctx: BindingContext,
    private val gClass: GClass,
    private val depth: Int = 0
) : DSGenerator(file, ctx) {

    //    private val randomInstancesGenerator = RandomInstancesGenerator(file)
    private val randomFunGenerator = RandomFunctionGenerator(file, ctx, gClass)

    private fun generatePropertyOverriding(
        isVar: Boolean,
        name: String,
        returnType: String,
        extReceiver: String?
    ): String {
        val ext = extReceiver?.let { "$extReceiver." } ?: ""
        val varOrVal = if (isVar) "var" else "val"
        val res = StringBuilder()
        if (gClass.isInterface()) return "\noverride $varOrVal $name: $returnType"
        res.append(
            "\noverride $varOrVal $ext$name: $returnType\n" +
                    "    get() = TODO()\n"
        )
        if (isVar) res.append("    set(value) {}\n")
        return res.toString()
    }

    fun getMembersToOverride(gClass: GClass, specifiers: List<KotlinType>): List<DeclarationDescriptor> {
        if (specifiers.isEmpty()) return listOf()
        return specifiers.flatMap { specifier ->
            val membersToOverride = StdLibraryGenerator.getMembersToOverride(specifier)
            if (gClass.let { it.isInterface() || it.isAbstract() })
                membersToOverride.filter { Random.getTrue(30) }
            else
                membersToOverride.filterNot {
                    when (it) {
                        is FunctionDescriptor -> it.modality == Modality.OPEN && Random.nextBoolean()
                        is PropertyDescriptor -> it.modality == Modality.OPEN && Random.nextBoolean()
                        else -> true
                    }
                }
        }.filterDuplicatesBy {
            if (it is FunctionDescriptor) "${it.name}${it.valueParameters.map { it.name.asString() + it.returnType.toString() }}"
            else (it as PropertyDescriptor).name.asString()
        }
    }

    fun generateOverrides(
        gClass: GClass,
        specifiers: List<KotlinType>,
        filteredMembers_: List<DeclarationDescriptor>? = null
    ): String {
        val filteredMembers = filteredMembers_ ?: getMembersToOverride(gClass, specifiers)
        if (filteredMembers.isEmpty()) return ""
        val res = StringBuilder()
        for (member in filteredMembers) {
            val rtv = member.toString().substringAfterLast(":").substringBefore(" defined")
            if (member is PropertyDescriptor) {
                res.append(
                    generatePropertyOverriding(
                        member.isVar,
                        member.name.asString(),
                        rtv,
                        member.extensionReceiverParameter?.value?.type?.toString()
                    )
                )
            } else if (member is FunctionDescriptor) {
                val f = member.toString().substringAfter("fun").substringBefore(" defined").split(" = ...")
                    .joinToString(" ")
                if ("$member".contains("suspend")) res.append("\noverride suspend fun$f = TODO()\n")
                else res.append("\noverride fun$f = TODO()\n")
            }
        }
        return res.toString()
    }

    private fun generateEnumFields() =
        with(StringBuilder()) {
            val constructorTypes = gClass.constructorArgs.map {
                it.split(":").let { randomTypeGenerator.generateType(it.last().substringBefore("=")) } ?: return ""
            }
            val isVar = Random.nextBoolean()
            val varOrVal = if (isVar) "var" else "val"
            val fieldToOverrideName = Random.getRandomVariableName()
            val fieldToOverride = if (Random.nextBoolean()) null else randomTypeGenerator.generateRandomTypeWithCtx()
            repeat(Random.nextInt(1, 4)) {
                val name = Random.getRandomVariableName(2).toUpperCase()
                val values =
                    constructorTypes
                        .map { randomInstancesGenerator.generateValueOfType(it) }
                        .map { if (it.trim().isEmpty()) "TODO()" else it }
                append("$name(${values.joinToString()})")
                appendLine('{')
                append(generateEnumEntryBody())
                fieldToOverride?.let {
                    append(generatePropertyOverriding(isVar, fieldToOverrideName, fieldToOverride.toString(), null))
                }
                appendLine('}')
                append(",\n")
            }
            replace(length - 2, length, ";\n")
            fieldToOverride?.let { append("abstract $varOrVal $fieldToOverrideName: $it\n") }
            this
        }.toString()

    private fun generateEnumEntryBody() =
        if (depth > MAX_DEPTH + 1) ""
        else with(StringBuilder()) {
            repeat(Random.nextInt(1, 3)) {
                val expr = when (Random.nextInt(0, 3)) {
                    0 -> RandomPropertyGenerator(file, ctx, gClass).generateRandomProperty(true)
                    1 -> randomFunGenerator.generate()?.text?.split("protected")?.joinToString(" ") ?: ""
                    else -> {
                        val gen = RandomClassGenerator(file, ctx)
                        val gClass = GClass()
                        gClass.modifiers = gen.generateModifiers()
                        gClass.modifiers[0] = "inner"
                        gen.finishGeneration(gClass)?.text ?: ""
                    }
                }
                appendLine(expr)
            }
            this
        }.toString()


    private fun generatePropWithAnonObj(): String {
        if (depth > MAX_DEPTH || Random.getTrue(80) || gClass.isInterface() || gClass.isInline()) return ""
        val res = StringBuilder()
        var withInheritance = false
        val lhv =
            if (Random.nextBoolean()) {
                (if (Random.nextBoolean()) "val" else "var") + " ${Random.getRandomVariableName()}"
            } else {
                randomFunGenerator.generate()?.text?.substringBeforeLast(':') ?: return ""
            }
        res.append(lhv)
        if (Random.nextBoolean()) {
            withInheritance = true
            val openKlass = randomTypeGenerator.generateOpenClassType(true) ?: return ""
            val instance =
                if (openKlass.modality == Modality.ABSTRACT)
                    ")"
                else if (openKlass.constructors.isEmpty() || openKlass.modality != Modality.OPEN)
                    null
                else
                    randomInstancesGenerator.generateValueOfType(openKlass.defaultType)
            val genTypeParams =
                if (instance == null || instance == ")" || instance.isEmpty()) {
                    openKlass.declaredTypeParameters.map { randomTypeGenerator.generateRandomTypeWithCtx().toString() }
                } else {
                    (Factory.psiFactory.createExpression(instance) as? KtCallExpression)?.typeArguments?.map { it.text }
                        ?: return ""
                }
            val tp = if (genTypeParams.isEmpty()) "" else genTypeParams.joinToString(prefix = " <", postfix = ">")
            val c = instance?.let { "(${it.substringAfter('(')}" } ?: ""
            val def = "object: ${openKlass.name}$tp$c"
            val typeWOTypeParams = randomTypeGenerator.generateType("${openKlass.name}$tp") ?: return ""
            val gClass = GClass()
            val overrides = generateOverrides(gClass, listOf(typeWOTypeParams))
            res.append(": ${openKlass.name}$tp")
            res.append(" = $def {")
            res.append(overrides)
        }
        if (!withInheritance) res.append(" = object {")
        val propsAndFuncs =
            generateFieldsForRegularClass(depth = Int.MAX_VALUE, true)
                .split("\n")
                .joinToString("\n") { if (it.trim().isNotEmpty() && !it.contains('=')) "$it = TODO()" else it }
        res.append(propsAndFuncs)
        res.append("}")
        return res.toString()
    }

    private fun generateFieldsForRegularClass(depth: Int = 0, fromObject: Boolean = false): String {
        val res = StringBuilder()
        if (gClass.isFunInterface()) {
            val f = randomFunGenerator.generate()?.text?.substringBeforeLast('=') ?: ""
            return "\n${f}\n"
        }
        with(res) {
            append(generatePropWithAnonObj())
            repeat(Random.nextInt(0, 4)) {
                append("\n")
                append(randomFunGenerator.generate()?.text ?: "")
                append("\n")
            }
            repeat(Random.nextInt(0, 4)) {
                append("\n")
                append(RandomPropertyGenerator(file, ctx, gClass).generateRandomProperty(fromObject))
                //append("val ${Random.getRandomVariableName(5)}: ${randomTypeGenerator.generateRandomTypeWithCtx()} = TODO()")
                append("\n")
            }
        }
        return res.toString()
    }

    private fun generateFields(depth: Int = 0): String {
        if (gClass.isAnnotation()) return ""
        if (gClass.isEnum()) return generateEnumFields()
        return generateFieldsForRegularClass(depth)
    }

    private fun generateSecondaryConstructor(): String {
        if (Random.getTrue(50)) return ""
        if (gClass.constructorArgs.isEmpty()) return ""
        if (gClass.constructorWord.contains("private")) return ""
        if (gClass.classWord != "class" || gClass.isAnnotation() || gClass.isAbstract() || gClass.isSealed()) return ""
        val newConstructor =
            RandomClassGenerator(file, ctx).also { it.gClass = gClass }.generateConstructor()
                .joinToString { it.substringAfter(' ') }
        val valueParams = gClass.constructorArgs
            .map { randomTypeGenerator.generateType(it.substringAfter(": ").substringBefore('=')) }
            .map { it ?: return "" }
            .map { randomInstancesGenerator.generateValueOfType(it).let { if (it.isEmpty()) return "" else it } }
            .joinToString(prefix = "(", postfix = ")")
        return if (Random.getTrue(20)) "private constructor($newConstructor):this$valueParams\n"
        else "constructor($newConstructor):this$valueParams\n"
    }

    private fun generateCompanionObject(): String {
        if (gClass.isInner() || Random.getTrue(70)) return ""
        if (gClass.classWord == "object" || gClass.isAnnotation()) return ""
        val tp = gClass.typeParams
        gClass.typeParams = listOf()
        val body = generateFieldsForRegularClass(depth, true)
        gClass.typeParams = tp
        return "companion object {\n$body\n}\n"
    }

    private fun generateInnerClass(forceGeneration: Boolean = false): String {
        if ((depth >= MAX_DEPTH || gClass.isInterface() || Random.nextBoolean()) && !forceGeneration) return ""
        if (gClass.isAnnotation() || gClass.isObject()) return ""
        val klassGenerator = RandomClassGenerator(file, ctx, depth + 1, gClass)
        val kl = klassGenerator.generate()
        return kl?.text ?: ""
    }

    fun generateBodyAsString(): String {
        val kTypeSpecifiers = gClass.supertypes.map {
            randomTypeGenerator.generateType(it.substringBefore('(').substringBefore(" by")) ?: return ""
        }
        return with(StringBuilder()) {
            append(generateOverrides(gClass, kTypeSpecifiers))
            append("\n")
            append(generateFields())
            //append("\n")
            //append(generateSecondaryConstructor())
            //append("\n")
            append(generateCompanionObject())
            append("\n")
            append(generateInnerClass())
            append("\n")
        }.toString()
    }

    override fun simpleGeneration(): PsiElement? =
        try {
            Factory.psiFactory.createBlock(generateBodyAsString())
        } catch (e: Exception) {
            null
        }

    override fun partialGeneration(initialStructure: GStructure): PsiElement? = simpleGeneration()
    override fun beforeGeneration() {}
    override fun afterGeneration(psi: PsiElement) {}


}

//TODO = 2
private const val MAX_DEPTH = 1