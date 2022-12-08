package com.stepanov.bbf.bugfinder.mutator.transformations.tce

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.mutator.transformations.filterDuplicates
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllChildren
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import java.io.File
import kotlin.random.Random
import kotlin.system.exitProcess

//TODO make work for projects
class TCETransformation : Transformation() {

    private val blockListOfTypes = listOf("Unit", "Nothing", "Nothing?")
    private val randomConst = 3
    private var psi = PSICreator.getPSIForText(file.text, false)
    lateinit var usageExamples: List<Triple<KtExpression, String, KotlinType?>>

    override fun transform() {
        for (i in 0 until randomConst) {
            log.debug("AFTER TRY $i res = ${psi.text}")
            usageExamples = collectUsageCases()
            log.debug("Try №$i")
            val line = File("database.txt").bufferedReader()
                .lines()
                .toArray()
                .toList()
                .map { it as String }
                .find { it.startsWith("FUN") }!!//.random()
            val randomType = line.takeWhile { it != ' ' }
            val files = File(CompilerArgs.baseDir).listFiles().map { it.name }
            val randomFile = files.random()
            var proj = Project.createFromCode(File("${CompilerArgs.baseDir}/$randomFile").readText())
            if (proj.language != LANGUAGE.KOTLIN) continue
            if (proj.files.size != 1)
                proj = proj.convertToSingleFileProject()
            if (!JVMCompiler().checkCompiling(proj)) continue
            val psi2 = proj.files.first().psiFile
            val anonProj = Project.createFromCode(psi2.text)
            //TODO!! anonProj.files.size >= 1
            if (anonProj.language != LANGUAGE.KOTLIN || anonProj.files.size != 1) continue
            //if (Project.createFromCode(psi2.text).language != LANGUAGE.KOTLIN) continue
            //val anonProj = Project.createFromCode(psi2.text)
            val anonPsi = anonProj.files.first().psiFile
            if (!Anonymizer.anon(anonProj)) {
                log.debug("Cant anonymize")
                continue
            }
            val sameTypeNodes = anonPsi.node.getAllChildrenNodes().filter { it.elementType.toString() == randomType }
            if (sameTypeNodes.isEmpty()) continue
            val targetNode = sameTypeNodes.random().psi as KtNamedFunction
            if (targetNode.getAllPSIChildrenOfType<KtExpression>().isEmpty()) continue
            log.debug("Trying to insert ${targetNode.text}")
            val psiBackup = psi.copy() as KtFile
            val addedNodes = addAllDataFromAnotherFile(psi, anonPsi as KtFile)
            if (!checker.checkCompilingWithFileReplacement(psi, checker.curFile)) {
                psi = psiBackup
                continue
            }
            psi = PSICreator.getPSIForText(psi.text)
            val updateAddedNodes =
                addedNodes.mapNotNull { n -> psi.getAllChildren().find { it.text.trim() == n.text.trim() } }
//            val newTargetNode = psi.getAllPSIChildrenOfType<KtNamedFunction>().find { it.name == targetNode.name }
//                ?: throw Exception("Cant find node")
            val ctx2 = PSICreator.analyze(psi) ?: continue
            replaceNodesOfFile(updateAddedNodes, ctx2)
        }
        log.debug("Final res = ${psi.text}")
        exitProcess(0)
        checker.curFile.changePsiFile(psi.text)
        //file = creator.getPSIForText(psi.text)
    }


    private fun replaceNodesOfFile(
        replaceNodes: List<PsiElement>,
        ctx: BindingContext
    ): Boolean {
        val fillerGenerator = FillerGenerator(psi, ctx, usageExamples.toMutableList())
        val replacementsList = mutableListOf<PsiElement>()
        var nodesForChange =
            updateReplacement(replaceNodes, ctx)
                .filterDuplicates { a: Pair<KtExpression, KotlinType?>, b: Pair<KtExpression, KotlinType?> ->
                    if (a.first == b.first) 0
                    else 1
                }
                .shuffled()
        log.debug("Trying to change ${nodesForChange.size} nodes")
        for (i in nodesForChange.indices) {
            if (i >= nodesForChange.size) break
            if (Random.getTrue(50)) continue
            val node = nodesForChange.randomOrNull() ?: continue
            log.debug("replacing ${node.first.text to node.second?.toString()}")
            node.first.parents.firstOrNull { it is KtNamedFunction }?.let { addPropertiesToFun(it as KtNamedFunction) }
            val replacement = fillerGenerator.getFillExpressions(node).randomOrNull()
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
            //node.first.replaceThis(replacement.copy())
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

    private fun addPropertiesToFun(node: KtNamedFunction) {
        val props = usageExamples.filter { it.first is KtProperty }
        val bodyBlockExpr = node.bodyBlockExpression ?: return
        val funProps = bodyBlockExpr.getAllPSIChildrenOfType<KtProperty>()
        props.reversed().forEach {
            val pr = it.first as KtProperty
            if (funProps.all { it.name != pr.name } && !node.text.contains(pr.text)) {
                node.bodyBlockExpression?.addProperty(pr)
            }
        }
    }

    private fun collectUsageCases(): List<Triple<KtExpression, String, KotlinType?>> {
        val ctx = PSICreator.analyze(psi) ?: return listOf()
        val generatedSamples = UsagesSamplesGenerator.generate(psi, ctx, checker.project)
        val boxFuncs = psi.getBoxFuncs() ?: return generatedSamples
        /*val properties =
            (boxFuncs.getAllPSIChildrenOfType<KtProperty>() + psi.getAllPSIChildrenOfType { it.isTopLevel })
                .map {
                    if (it.typeReference != null) Triple(it, it.text, it.typeReference!!.getAbbreviatedTypeOrType(ctx))
                    else Triple(it, it.text, it.initializer?.getType(ctx))
                }
                .filter { it.third != null && !it.third!!.isError }*/
        val destrDecl = boxFuncs.getAllPSIChildrenOfType<KtDestructuringDeclaration>()
            .map { Triple(it, it.text, it.initializer?.getType(ctx)) }
            .filter { it.third != null && !it.third!!.isError }
        val exprs = boxFuncs.getAllPSIChildrenOfType<KtExpression>()
            .filter { it !is KtProperty }
            .filterDuplicatesBy { it.text }
            .map { Triple(it, it.text, it.getType(ctx)) }
            .filter {
                it.third != null &&
                        !it.third!!.isError &&
                        it.first !is KtStringTemplateEntry &&
                        it.first !is KtConstantExpression &&
                        it.first.node.elementType != KtNodeTypes.STRING_TEMPLATE &&
                        !it.first.parent.text.endsWith(it.first.text)
            }
        return (destrDecl + exprs).map { Triple(it.first, it.second, it.third!!) } + generatedSamples
    }

    private fun addAllDataFromAnotherFile(
        file: KtFile,
        anotherFile: KtFile,
        except: List<PsiElement> = listOf()
    ): List<PsiElement> {
        val topLevelNodes =
            anotherFile.getAllChildren()
                .filter { it.isTopLevelKtOrJavaMember() && it !is KtImportList && it !in except }
        //val block = psiFactory.createBlock("\n\n" + topLevelNodes.joinToString("\n") { it.text })
        //block.lBrace?.delete()
        //block.rBrace?.delete()
        anotherFile.importDirectives.forEach { file.addImport(it) }
        val addedNodes = mutableListOf<PsiElement>()
        topLevelNodes.forEach { node ->
            file.getAllPSIDFSChildrenOfType<PsiElement>().last().parent.let {
                it.add(psiFactory.createWhiteSpace("\n\n"))
                addedNodes.add(it.add(node))
                it.add(psiFactory.createWhiteSpace("\n\n"))
            }
        }
        return addedNodes
    }

    private fun updateReplacement(nodes: List<PsiElement>, ctx: BindingContext) =
        nodes.flatMap { updateReplacement(it, ctx) }

    private fun updateReplacement(node: PsiElement, ctx: BindingContext) =
        node.getAllPSIChildrenOfType<KtExpression>()
            .map { it to it.getType(ctx) }
            .filter {
                it.second != null &&
                        !it.second!!.isError &&
                        it.second.toString() !in blockListOfTypes &&
                        it.second?.toString()?.contains("name provided") == false
            }
            .filter { if (it is KtSimpleNameExpression) it.getReceiverExpression() == null else true }

    private fun Random.getTrue(prob: Int): Boolean =
        Random.nextInt(0, 100) < prob
}