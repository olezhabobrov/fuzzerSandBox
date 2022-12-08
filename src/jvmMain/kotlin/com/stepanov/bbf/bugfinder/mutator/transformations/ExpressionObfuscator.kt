package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.TCEUsagesCollector
import com.stepanov.bbf.bugfinder.mutator.transformations.util.FileFieldsTable
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getNameWithoutError
import com.stepanov.bbf.bugfinder.util.getTrue
import com.stepanov.bbf.bugfinder.util.isErrorType
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import java.lang.StringBuilder
import java.util.ArrayList
import kotlin.random.Random
import kotlin.system.exitProcess

class ExpressionObfuscator : Transformation() {

    lateinit var usageExamples: MutableList<Triple<KtExpression, String, KotlinType?>>
    lateinit var availableVars: List<Pair<KtProperty, KotlinType?>>
    var randomInstanceGenerator: RandomInstancesGenerator? = null

    override fun transform() {
        var ctx = PSICreator.analyze(file) ?: return
        val ktFile = file as? KtFile ?: return
        randomInstanceGenerator = RandomInstancesGenerator(ktFile, ctx)
        usageExamples = TCEUsagesCollector.collectUsageCases(file as KtFile, ctx, checker.project).toMutableList()
        //TODO make dependent from size of program
        repeat(Random.nextInt(10, 20)) { it ->
            ctx = PSICreator.analyze(file) ?: return
            val (expr, exprType) = getRandomExpressionForObfuscation(ctx) ?: return@repeat
            calcAvailableVariables(expr, ctx)
            val callList = StdLibraryGenerator.gen(exprType.makeNotNullable(), exprType.makeNotNullable())
            if (callList.isEmpty()) {
                return@repeat
            }
            val randomDescr = callList.random()
            val callSequence = createCallSequence(expr, randomDescr) ?: return@repeat
            val replacedCallSequence = checker.replaceNodeIfPossibleWithNode(expr.node, callSequence.node)
            if (replacedCallSequence != null) {
                ctx = PSICreator.analyze(file)!!
                replacedCallSequence.psi.getAllPSIChildrenOfType<KtExpression>()
                    .map { it to it.getType(ctx) }
                    .filter { it.second != null && it.first !is KtCallExpression }
                    .reversed()
                    .forEach { (e, t) ->
                        if (Random.getTrue(50)) return@forEach
                        val tName = t!!.getNameWithoutError()
                        val sameTypeAvailableExpressionsFromGeneratedUsages =
                            usageExamples
                                .filter { it.third?.getNameWithoutError() == tName }
                                .map { it.first.text }
                        val sameTypeAvailableExpressionsFromAvProperties =
                            availableVars
                                .filter { it.second?.getNameWithoutError() == tName }
                                .map { it.first.name!! }
                        val allSameTypeAvailableExpressions =
                            sameTypeAvailableExpressionsFromAvProperties + sameTypeAvailableExpressionsFromGeneratedUsages
                        allSameTypeAvailableExpressions.randomOrNull()?.let {
                            val exp = Factory.psiFactory.createExpressionIfPossible(it) ?: return@forEach
                            checker.replaceNodeIfPossible(e, exp)
                        }
                    }
            }
        }
    }

    private fun calcAvailableVariables(expr: KtExpression, ctx: BindingContext) {
        availableVars = getSlice(expr)
            .mapNotNull {
                when (it) {
                    is KtProperty ->
                        it to (it.typeReference?.getAbbreviatedTypeOrType(ctx) ?: it.initializer?.getType(ctx))
                    else -> null
                }
            }
            .filter { it.second != null }
            .filterDuplicates { t, t2 -> t.first.text.compareTo(t2.first.text) }
    }

    private fun getRandomExpressionForObfuscation(ctx: BindingContext): Pair<KtExpression, KotlinType>? {
        val randomExpr = file.getAllPSIChildrenOfType<KtExpression>()
            .map { it to it.getType(ctx) }
            .filter { it.second != null }
            .filter { !it.second!!.isUnit() && !it.second!!.isErrorType() && !it.second!!.isNothing() }
            .randomOrNull() ?: return null
        return randomExpr.first to randomExpr.second!!
    }

    //We are not expecting typeParams
    private fun generateCallExpr(func: CallableDescriptor): KtExpression? {
        log.debug("GENERATION of call of type $func")
        val name = func.name
        val valueParams = func.valueParameters.map { vp ->
            val fromUsages =
                usageExamples.shuffled().find { it.third?.getNameWithoutError() == vp.type.getNameWithoutError() }
            val fromAvailableVars =
                availableVars.shuffled().find { it.second?.getNameWithoutError() == vp.type.getNameWithoutError() }
            var newVPValue =
                if (fromAvailableVars != null) {
                    val prob = if (fromUsages == null) 75 else 50
                    if (Random.getTrue(prob)) fromAvailableVars.first.name
                    else null
                } else null
            if (newVPValue == null) {
                newVPValue =
                    if (fromUsages != null && Random.getTrue(75)) fromUsages.first.text
                    else randomInstanceGenerator!!.generateValueOfType(vp.type).ifEmpty { "TODO()" }
            }
            newVPValue ?: "TODO()"
        }
        val inv = "$name(${valueParams.joinToString()})"
        return Factory.psiFactory.createExpressionIfPossible(inv)
    }

    private fun createCallSequence(expr: KtExpression, callList: List<CallableDescriptor>): KtExpression? {
        val res = StringBuilder()
        var prefix = "${expr.text}."
        callList.map { desc ->
            val expr = when (desc) {
                is PropertyDescriptor -> desc.name.asString()
                is FunctionDescriptor -> generateCallExpr(desc)?.text
                else -> ""
            }
            expr ?: return null
            res.append(prefix)
            prefix = if (desc.returnType?.isNullable() == true) "?." else "."
            res.append(expr)
        }
        if (callList.any { it.returnType?.isNullable() == true })
            res.append("!!")
        return Factory.psiFactory.createExpression(res.toString())
    }
}