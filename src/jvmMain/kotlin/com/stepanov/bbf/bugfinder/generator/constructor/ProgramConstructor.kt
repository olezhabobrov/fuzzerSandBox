package com.stepanov.bbf.bugfinder.generator.constructor

import com.stepanov.bbf.bugfinder.executor.checkers.CompilationChecker
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.generateDefValuesAsString
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.replaceThis
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.resolve.calls.callUtil.getType

class ProgramConstructor(private val checker: CompilationChecker) {

    fun construct(): KtFile? {
        addDataStructures()
        if (!checker.checkCompiling(Project.createFromCode(resFile.text))) return null
        addFunctions()
        println("RES = ${resFile.text}")
        System.exit(0)
        return resFile
    }

    private fun addDataStructures() {
        for (i in 0 until 3) {
            val ds = NodeDB.getRandomDataStructures(i)
            ds.forEach {
                constructionContext.classesAndUsages[it.key] = it.value
                if (!it.key.psi.isTopLevelKtOrJavaMember()) return@forEach
                resFile.add(it.key.psi)
                resFile.add(Factory.psiFactory.createWhiteSpace("\n\n"))
                it.value?.let { value -> value.filter { it.node.psi is KtNamedFunction }.forEach {
                    resFile.add(it.node.psi)
                    resFile.add(Factory.psiFactory.createWhiteSpace("\n\n"))
                } }
            }
        }
        constructionContext.classesAndUsages = constructionContext.classesAndUsages.entries
            .map { it.key to it.value?.filter { it.node.elementType != KtNodeTypes.FUN } }
            .toMap().toMutableMap()
    }

    private fun addFunctions() {
        val (func, ctx) = NodeDB.getRandomNodeOfTypeFromRandomFileWithCtx(KtNodeTypes.FUN).let { it.first.psi to it.second }
        println("BEFORE = ${func.text}")
        val expToReplace = func.getAllPSIChildrenOfType<KtExpression>()
            .map { it to it.getType(ctx) }
            .filter { it.second != null && it.second!!.toString() != "Nothing"}
        val used = mutableListOf<UsingExample>()
        for ((expr, type) in expToReplace) {
            println("SEARCHING FOR EXPRESSION OF TYPE ${type}")
            val randomInstanceOfType = constructionContext.getUsageOfType(type!!)
            println(randomInstanceOfType != null)
            if (randomInstanceOfType != null && used.all { it.node != randomInstanceOfType.node }) {
                println("REPLACING ${expr.text} on ${randomInstanceOfType.node.text} of type ${randomInstanceOfType.type}")
                used.add(randomInstanceOfType)
                expr.replaceThis(randomInstanceOfType.node.psi)
                println("RES = ${func.text}")
            } else {
                val generated =  generateDefValuesAsString(type.toString())
                println("GENERATED = ${generated}")
                val generatedExpr = Factory.psiFactory.createExpressionIfPossible(generated) ?: continue
                expr.replaceThis(generatedExpr)
            }
        }
        println(func.text)
    }

    val constructionContext = ConstructionContext()
    val resFile = Factory.psiFactory.createFile("")
}