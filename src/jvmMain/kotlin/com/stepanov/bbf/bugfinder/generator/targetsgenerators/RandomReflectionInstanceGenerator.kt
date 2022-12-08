package com.stepanov.bbf.bugfinder.generator.targetsgenerators

import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedPropertyDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjection
import kotlin.random.Random
import kotlin.system.exitProcess

//TODO finish it'
class RandomReflectionInstanceGenerator(
    private val file: KtFile,
    private val type: KotlinType,
    private val ctx: BindingContext
) : RandomInstancesGenerator(file, ctx) {

    private fun getParentClassesNames(kl: KtClass) =
        kl.parents.toList()
            .filter { it is KtClass && it.name != null }.joinToString(".") { (it as KtClass).name!! }
            .let { if (it.isNotEmpty()) "$it." else "" }

    private fun generateKClass(): String {
        return if (type.arguments.first().isStarProjection) {
            val fileClasses =
                file.getAllPSIChildrenOfType<KtClass>()
                    .filter { it.isTopLevel() }
                    .filter { it.name != null }
            if (Random.nextBoolean() && fileClasses.isNotEmpty()) {
                val randomKlass = fileClasses.random()
                getParentClassesNames(randomKlass) + randomKlass.name + "::class"
            } else {
                randomTypeGenerator.generateRandomType().substringBefore('<') + "::class"
            }
        } else {
            "${type.arguments.first().type.toString().substringBefore('<')}::class"
        }
    }

    private fun generateKFunction(): String {
        return ""
    }

    private fun generateKProperty0(arg: TypeProjection): String {
        val fromFile =
            file.getAllPSIChildrenOfType<KtProperty>().filter { it.isTopLevel }
        if (arg.isStarProjection) return ""
        return ""
    }

    private fun generateKProperty(): String {
        val props = StdLibraryGenerator.descriptorDecl.filter { it is DeserializedPropertyDescriptor }
        val args = type.arguments
        when (args.size) {
            1 -> ""
            2 -> ""
            3 -> ""
        }
        println("CANT GENERATE KPROP BECAUSE NOBODY DIDNT IMPLEMENT IT =(((")
        return ""
    }

    fun generateReflection(): String {
        val strType = type.toString()
        return when {
            strType.startsWith("KClass") -> generateKClass()
            strType.startsWith("KFunction") -> generateKFunction()
            else -> generateKProperty()
        }
    }
}