package com.stepanov.bbf.bugfinder.reflektTester

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.getPath
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import kotlin.collections.flatMap
import kotlin.system.exitProcess

//TODO make it for object
class QueryGenerator(private val project: Project) {

    val projectFiles = project.files.map { it.psiFile as KtFile }
    val ctx = PSICreator.analyze(projectFiles.first(), project)

    fun generateQuery(): KtFile? {
        if (ctx == null) return null
        val queries1 = generateClassQueries()
        val queries2 = generateFunctionQueries()
        val allQueries = queries1 + queries2
        println("QUERIES = ${allQueries.size}")
        if (allQueries.size == 0) return null
        val fileText = "package io.reflekt.example\n\n" +
                "import io.reflekt.SmartReflekt\n" +
                "import io.reflekt.Reflekt\n\n" +
                "fun main() { \n" + allQueries.joinToString("\n") { it.text } + "\n}"
        val mainFile = Factory.psiFactory.createFile(fileText)
        val topLevelClasses =
            projectFiles
                .flatMap { it.getAllPSIChildrenOfType<KtClass>() }
                .filter { it.isTopLevel() }
                .mapNotNull { it.getDeclarationDescriptorIncludingConstructors(ctx)?.fqNameOrNull()?.asString() }
        topLevelClasses.forEach {
            mainFile.addImport(it, false)
        }
        return mainFile
    }

    private fun generateClassQueries(): List<KtExpression> {
        val classList =
            projectFiles.flatMap { it.getAllPSIChildrenOfType<KtClass>() }
                .filter { it.typeParameters.isEmpty() && it !is KtEnumEntry }
                .mapNotNull {
                    val descriptor = it.getDeclarationDescriptorIncludingConstructors(ctx!!) as? ClassDescriptor
                    if (descriptor != null) {
                        val fullName = descriptor.fqNameSafe
                        val expr = """SmartReflekt.classes<$fullName>().filter { true }.resolve()"""
                        Factory.psiFactory.createExpressionIfPossible(expr)
                    } else null
                }
        return classList
    }

    private fun generateFunctionQueries(): List<KtExpression> {
        val funList =
            projectFiles.flatMap { it.getAllPSIChildrenOfType<KtNamedFunction>() }
                .filter { it.name != "box" }
                .map { it to it.getReturnType(ctx!!) }
                .filter {
                    it.second != null && !it.second!!.isUnderTemporaryRestrictions()
                }
                .filter { it.second!!.getAllTypeParamsWithItself().isEmpty() }
                .map { it.first }

        val simpleQueries = funList.mapNotNull {
            var functionDescriptor = it.getDeclarationDescriptorIncludingConstructors(ctx!!) as? FunctionDescriptor
            if (functionDescriptor?.valueParameters?.any { it.returnType?.getAllTypeParamsWithItself()?.isNotEmpty() == true } == true) {
                functionDescriptor = null
            }
            if (functionDescriptor != null) {
                val rtv = it.getReturnType(ctx)?.getNameWithoutError() ?: "Unit"
                val valueParamsTypes = functionDescriptor.valueParameters.map { it.type.getNameWithoutError() }
                val vpTypesAsString = if (valueParamsTypes.isEmpty()) "" else valueParamsTypes.joinToString(", ")
                val funType =
                    if (vpTypesAsString.isEmpty()) "Function0<$rtv>" else "Function${valueParamsTypes.size}<$vpTypesAsString, $rtv>"
                val expression = """SmartReflekt.functions<$funType>().filter { true }.resolve()"""
                expression
            } else null
        }.toSet().mapNotNull { Factory.psiFactory.createExpressionIfPossible(it) }
        return simpleQueries
    }

    private fun KotlinType.isUnderTemporaryRestrictions(): Boolean =
        KotlinBuiltIns.isPrimitiveTypeOrNullablePrimitiveType(this)
}