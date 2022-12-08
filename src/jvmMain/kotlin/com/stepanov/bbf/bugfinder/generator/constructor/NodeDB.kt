package com.stepanov.bbf.bugfinder.generator.constructor

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import com.stepanov.bbf.bugfinder.util.getType as getType1
import java.io.File
import kotlin.random.Random

object NodeDB {

    // DataStructure to using examples
    fun getRandomDataStructures(ind: Int): Map<ASTNode, List<UsingExample>?> {
        val resMap = mutableMapOf<ASTNode, List<UsingExample>?>()
//        val type =
//            when (Random.nextInt(0, 10)) {
//                0 -> KtNodeTypes.OBJECT_DECLARATION
//                else -> KtNodeTypes.CLASS
//            }
        //val psiWithNeededNodes = getRandomFileWithNodesOfType(type)
        val psiWithNeededNodes = listOf("boxNullableInt.kt", "kt2399.kt", "classifierIsClass.kt")
            .map { Factory.psiFactory.createFile(File("${CompilerArgs.baseDir}/$it").readText()) }[ind]
//        if (!anonymize(psiWithNeededNodes, checker)) {
//            println(psiWithNeededNodes.text)
//            return mapOf()
//        }
        val dataStructures = psiWithNeededNodes.getAllPSIChildrenOfType<KtClassOrObject>()
        val ctx = PSICreator.analyze(psiWithNeededNodes) ?: return mapOf()
        for (ds in dataStructures) {
            if (ds.name == null) continue
            val usingExamples =
                psiWithNeededNodes
                    .getAllPSIChildrenOfType<KtExpression> { it.text.contains(ds.name!!) }
                    .map { it to it.getType(ctx) }
                    .filter { it.first !is KtClassOrObject && it.second != null && it.second.toString() != "Nothing" }
                    .map { UsingExample(it.first.node, it.second!!) }+
                psiWithNeededNodes
                    .getAllPSIChildrenOfType<KtNamedFunction>() { !it.text.contains("fun box") && it.isTopLevel}
                    .map { it to (it.typeReference?.getAbbreviatedTypeOrType(ctx) ?: it.getType1(ctx)) }
                    .filter { it.second != null }
                    .map { UsingExample(it.first.node, it.second!!) }
            resMap[ds.node] =
                if (usingExamples.isEmpty())
                    null
                else
                    usingExamples

        }
        //println(resMap.entries.map { it.value?.map { it.node.text to it.type } })
        return resMap
    }

//    private fun anonymize(psi: KtFile, checker: Checker): Boolean {
//        val named = mutableListOf<KtNamedDeclaration>()
//        for (child in psi.getAllChildren()) {
//            if (child is KtNamedDeclaration && child.name != null && !child.name!!.startsWith("<") &&
//                !child.name!!.contains("box")
//            ) {
//                if (named.all { it.name != child.name })
//                    named.add(child)
//            }
//        }
//        for (n in named) {
//            val newName = when (n) {
//                is KtClassOrObject -> "Kl${counters[0]++}"
//                is KtFunction -> "f${counters[1]++}"
//                is KtProperty -> "prop${counters[2]++}"
//                is KtParameter -> "par${counters[3]++}"
//                else -> "smth${counters[4]++}"
//            }
//            val usages = psi.getAllPSIChildrenOfType<KtNameReferenceExpression>().filter { it.text == n.name }
//            val newNameRef = KtPsiFactory(psi.project).createSimpleName(newName)
//            usages.map {
//                val copy = newNameRef.copy()
//                it.replaceThis(copy)
//                it.copy() to copy
//            }
//            n.setName(newName)
//        }
//        return checker.checkCompiling(Project.createFromCode(psi.text))
//    }
//
//    fun reinitCounters() {
//        counters.replaceAll { 0 }
//    }

    private fun getRandomFileWithNodesOfType(type: IElementType): KtFile {
        val line = file.filter { it.takeWhile { it != ' ' } == type.toString() }.first()
        val files = line.dropLast(1).takeLastWhile { it != '[' }.split(", ")
        val randomFile = files.random()
        //val randomFile = "singleElementStepTwo0.kt"
        //println("NAME = $randomFile")
        val psi = Factory.psiFactory.createFile(File("${CompilerArgs.baseDir}/$randomFile").readText())
        return psi
    }

    fun getAllNodesOfTypeFromRandomFile(type: IElementType): List<ASTNode> {
        val psi = getRandomFileWithNodesOfType(type)
        return psi.node.getAllChildrenNodes().filter { it.elementType == type }
    }

    fun getAllNodesOfTypeFromRandomFileWithCtx(type: IElementType): Pair<List<ASTNode>, BindingContext> {
        val psi = getRandomFileWithNodesOfType(type)
        val ctx = PSICreator.analyze(psi)
        return psi.node.getAllChildrenNodes().filter { it.elementType == type } to ctx!!
    }

    fun getRandomNodeOfTypeFromRandomFile(type: IElementType): ASTNode = getAllNodesOfTypeFromRandomFile(type).random()
    fun getRandomNodeOfTypeFromRandomFileWithCtx(type: IElementType): Pair<ASTNode, BindingContext> =
        getAllNodesOfTypeFromRandomFileWithCtx(type).let { it.first.random() to it.second }


    private val file = File("database.txt").bufferedReader().lines().toArray().toList().map { it as String }
    private val counters = mutableListOf(0, 0, 0, 0, 0)
}