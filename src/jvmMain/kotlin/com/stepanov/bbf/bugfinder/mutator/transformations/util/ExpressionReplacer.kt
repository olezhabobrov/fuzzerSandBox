package com.stepanov.bbf.bugfinder.mutator.transformations.util

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.tryToCreateExpression
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.util.ScopeCalculator.Companion.processScope
import com.stepanov.bbf.bugfinder.util.getNameWithoutError
import com.stepanov.bbf.bugfinder.util.getTrue
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.isUnit
import java.lang.StringBuilder
import kotlin.random.Random

//Class which trying to replace expression of one type by expressions from available context
class ExpressionReplacer : Transformation() {

    private val blockListOfTypes = listOf("Nothing", "Nothing?")
    private val generatedFunCalls = mutableMapOf<FunctionDescriptor, KtExpression?>()
    private var rig: RandomInstancesGenerator? = null

    override fun transform() {
        val ktFile = file as KtFile
        val ctx = PSICreator.analyze(checker.curFile.psiFile, checker.project) ?: return
        rig = RandomInstancesGenerator(ktFile, ctx)
        RandomTypeGenerator.setFileAndContext(ktFile, ctx)
        var nodesToChange = updateReplacement(ktFile.getAllChildren(), ctx).shuffled()
        for (ind in nodesToChange.indices) {
            if (ind >= nodesToChange.size) break
            if (nodesToChange[ind].second!!.isUnit() && Random.getTrue(80)) continue
            else if (Random.getTrue(60)) continue
            replaceExpression(nodesToChange[ind].first, nodesToChange[ind].second!!)
            nodesToChange = updateReplacement(ktFile.getAllChildren(), ctx).shuffled()
        }
    }

    private fun replaceExpression(exp: KtExpression, expType: KotlinType): Boolean {
        if (expType.getNameWithoutError() in blockListOfTypes) return false
        val processedScope = ScopeCalculator(file as KtFile, project).run {
            processScope(rig!!, calcScope(exp).shuffled(), generatedFunCalls)
        }
        val randomExpressionToReplace = getRandomExpressionToReplace(expType, processedScope) ?: return false
        return checker.replaceNodeIfPossible(exp, randomExpressionToReplace)
    }

    private fun handleCallSeq(postfix: List<CallableDescriptor>, scope: List<ScopeCalculator.ScopeComponent>): KtExpression? {
        val res = StringBuilder()
        var prefix = ""
        postfix.map { desc ->
            val expr = when (desc) {
                is PropertyDescriptor -> desc.name.asString()
                is FunctionDescriptor -> ScopeCalculator.generateCallExpr(rig!!, desc, scope)?.text
                else -> ""
            }
            expr ?: return null
            res.append(prefix)
            prefix = if (desc.returnType?.isNullable() == true) "?." else "."
            res.append(expr)
        }
        return Factory.psiFactory.createExpression(res.toString())
    }

    private fun getRandomExpressionToReplace(
        needType: KotlinType,
        scope: List<ScopeCalculator.ScopeComponent>
    ): KtExpression? {
        if (needType.isUnit()) {
            return scope
                .firstOrNull { it.type.isUnit() }
                ?.let { Factory.psiFactory.tryToCreateExpression(it.psiElement.text) }
        }
        if (needType.isTypeParameter()) {
            return scope
                .filter { it.declaration !is FunctionDescriptor }
                .firstOrNull { it.type.isTypeParameter() }
                ?.let { Factory.psiFactory.tryToCreateExpression(it.psiElement.text) }
        }
        val localRes = mutableListOf<KtExpression>()
        for (scopeEl in scope) {
            if (scopeEl.type.isTypeParameter()) continue
            //println("TRYING TO GET TYPE $needType from ${scopeEl.psiElement.text} of type ${scopeEl.type}")
            val expressionToUse = scopeEl.psiElement.copy() as? KtExpression ?: continue
            if (expressionToUse.text.isEmpty()) continue
            val typeDescriptorOfUsage = scopeEl.type.constructor.declarationDescriptor
            if (typeDescriptorOfUsage?.defaultType == needType.constructor.declarationDescriptor?.defaultType) {
                localRes.add(expressionToUse)
            }
            when {
                scopeEl.type.getNameWithoutError() == "$needType" -> {
                    localRes.add(expressionToUse)
                }
                scopeEl.type.getNameWithoutError() == "$needType?" -> {
                    localRes.add(expressionToUse)
                }
                StdLibraryGenerator.isImplementation(needType, scopeEl.type) -> {
                    localRes.add(expressionToUse)
                }
            }
            if (needType.isNullable()) localRes.add(Factory.psiFactory.createExpression("null"))
            StdLibraryGenerator.generateCallSequenceToGetType(scopeEl.type, needType)
                .filter { it.isNotEmpty() }
                .shuffled()
                .take(5)
                .forEach { list ->
                    log.debug("Case = ${list.map { it }}")
                    handleCallSeq(list, scope)?.let {
                        val prefix = "(${expressionToUse.text})" + if (needType.isNullable()) "?." else "."
                        val exp = "$prefix${it.text}"
                        val postfix = if (exp.contains("?.") && !needType.isNullable()) "!!" else ""
                        log.debug("Trying to generate expression: $exp$postfix")
                        Factory.psiFactory.tryToCreateExpression("$exp$postfix")?.let {
                            log.debug("GENERATED CALL = ${it.text}")
                            localRes.add(it)
                        }
                    }
                }
            if (localRes.isNotEmpty()) {
                return localRes.random()
            }
        }
        return null
    }

//    fun handleCallSeq(postfix: List<CallableDescriptor>, scope: List<ScopeCalculator.ScopeComponent>): KtExpression? {
//        val res = StringBuilder()
//        var prefix = ""
//        postfix.map { desc ->
//            val expr = when (desc) {
//                is PropertyDescriptor -> desc.name.asString()
//                is FunctionDescriptor -> generateCallExpr(desc, scope)?.text
//                else -> ""
//            }
//            expr ?: return null
//            res.append(prefix)
//            prefix = if (desc.returnType?.isNullable() == true) "?." else "."
//            res.append(expr)
//        }
//        return Factory.psiFactory.createExpression(res.toString())
//    }
//
//    //We are not expecting typeParams
//    private fun generateCallExpr(
//        func: CallableDescriptor,
//        scopeElements: List<ScopeCalculator.ScopeComponent>
//    ): KtExpression? {
//        log.debug("GENERATING call of type $func")
//        val name = func.name
//        val valueParams = func.valueParameters.map { vp ->
//            val fromUsages = scopeElements.filter { usage ->
//                vp.type.getNameWithoutError().trim() == usage.type.getNameWithoutError().trim()
//            }
//            if (fromUsages.isNotEmpty() && Random.getTrue(80)) fromUsages.random().psiElement.text
//            else rig!!.generateValueOfType(vp.type)
//            //getInsertableExpressions(Pair(it, it.typeReference?.getAbbreviatedTypeOrType()), 1).randomOrNull()
//        }
//        if (valueParams.any { it.isEmpty() }) {
//            log.debug("CANT GENERATE PARAMS FOR $func")
//            return null
//        }
//        val inv = "$name(${valueParams.joinToString()})"
//        return Factory.psiFactory.tryToCreateExpression(inv)
//    }

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