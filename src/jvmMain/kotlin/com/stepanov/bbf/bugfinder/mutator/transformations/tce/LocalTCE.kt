package com.stepanov.bbf.bugfinder.mutator.transformations.tce

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isUnit
import kotlin.random.Random
import kotlin.system.exitProcess

class LocalTCE : Transformation() {

    private val blockListOfTypes = listOf("Unit", "Nothing", "Nothing?")
    private var psi = file.copy() as KtFile
    lateinit var usageExamples: MutableList<Triple<KtExpression, String, KotlinType?>>

    override fun transform() {
        val ctx = PSICreator.analyze(psi) ?: return
        RandomTypeGenerator.setFileAndContext(file as KtFile, ctx)
        usageExamples = TCEUsagesCollector.collectUsageCases(psi, ctx, checker.project).toMutableList()
        addRandomUnitCalls()
        replaceNodesOfFile(psi.getAllPSIChildrenOfType(), ctx)
        checker.curFile.changePsiFile(psi.text)
    }

    private fun addRandomUnitCalls() {
        val unitCalls = usageExamples.filter { it.third?.isUnit() ?: false }.filter { Random.getTrue(80) }
        for (call in unitCalls) {
            val backupFile = psi.copy() as KtFile
            val randomPlaceToInsert =
                psi.getAllPSIChildrenOfType<PsiElement>()
                    .filter { it.nextSibling.let { it is PsiWhiteSpace && it.text.contains("\n") } }
                    .randomOrNull() ?: continue
            randomPlaceToInsert.addAfterThisWithWhitespace(call.first.copy(), "\n")
            if (!checker.checkCompilingWithFileReplacement(psi, checker.curFile)) {
                psi = backupFile
            }
        }
    }

    private fun replaceNodesOfFile(
        replaceNodes: List<PsiElement>,
        ctx: BindingContext
    ): Boolean {
        val fillerGenerator = FillerGenerator(psi, ctx, usageExamples)
        val replacementsList = mutableListOf<PsiElement>()
        var nodesForChange = updateReplacement(replaceNodes, ctx).shuffled()
        log.debug("Trying to change ${nodesForChange.size} nodes")
        for (i in nodesForChange.indices) {
            log.debug("Node $i from ${nodesForChange.size}")
            if (Random.getTrue(60)) continue
            if (i >= nodesForChange.size) break
            val node = nodesForChange.randomOrNull() ?: continue
            log.debug("trying to replace ${node} ${node.first.text}")
            val oldUESize = usageExamples.size
            psi.getAvailableValuesToInsertIn(node.first, ctx).forEach { v ->
                if (v.second != null) usageExamples.add(Triple(v.first, v.first.text, v.second))
            }
            log.debug("replacing ${node.first.text to node.second?.toString()}")
            //node.first.parents.firstOrNull { it is KtNamedFunction }?.let { addPropertiesToFun(it as KtNamedFunction) }
            val replacement = fillerGenerator.getFillExpressions(node).randomOrNull()
            while (usageExamples.size != oldUESize) usageExamples.removeLast()
            if (replacement == null) {
                log.debug("Cant find and generate replacement for ${node.first.text} type ${node.second}")
                continue
            }
            log.debug("replacement of ${node.first.text} of type ${node.second} is ${replacement.text}")
            checker.replaceNodeIfPossibleWithNodeWithFileReplacement(
                psi,
                checker.curFile,
                node.first.node,
                replacement.copy().node
            )?.let { replacementsList.add(it.psi) }
            nodesForChange = updateReplacement(replaceNodes, ctx)
        }
        changeValuesInExpression(replacementsList)
        return true
    }


    private fun changeValuesInExpression(nodeList: List<PsiElement>) {
        val ctx = PSICreator.analyze(psi) ?: return
        val constants = nodeList
            .flatMap { it.getAllPSIChildrenOfType<KtConstantExpression>() }
            .map { it to it.getType(ctx) }
            .filter { it.second != null }
        val psiExpToType = psi.getAllPSIChildrenOfType<KtExpression>()
            .map { it to it.getType(ctx) }
            .filter { it.second != null }
        val expToType = usageExamples.map { it.first to it.third } + psiExpToType
        for (constant in constants) {
            val sameTypeExpression =
                expToType.filter { it.second!!.toString() == constant.second!!.toString() }.randomOrNull()
            sameTypeExpression?.let {
                log.debug("TRYING TO REPLACE CONSTANT ${constant.first.text}")
                if (constant.first.parent is KtPrefixExpression) {
                    checker.replacePSINodeIfPossibleWithFileReplacement(
                        psi,
                        checker.curFile,
                        constant.first.parent,
                        it.first
                    )
                } else checker.replacePSINodeIfPossibleWithFileReplacement(
                    psi,
                    checker.curFile,
                    constant.first,
                    it.first
                )
            }
        }
    }

    private fun updateReplacement(nodes: List<PsiElement>, ctx: BindingContext) =
        nodes
            .asSequence()
            .filterIsInstance<KtExpression>()
            .map { it to it.getType(ctx) }
            .filter {
                it.second != null &&
                        !it.second!!.isError &&
                        it.second.toString() !in blockListOfTypes &&
                        it.second?.toString()?.contains("name provided") == false
            }
            .filter { if (it is KtSimpleNameExpression) it.getReceiverExpression() == null else true }
            .filter { it.first.parent !is KtDotQualifiedExpression }
            .toList()
}